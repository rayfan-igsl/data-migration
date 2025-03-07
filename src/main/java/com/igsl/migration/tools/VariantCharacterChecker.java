package com.igsl.migration.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

@Component
public class VariantCharacterChecker {
    private static final Logger logger = LogManager.getLogger(VariantCharacterChecker.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Set<String> loadUnicodeSet() {
        try {
            Set<String> unicodeSet = new HashSet<>(Files.readAllLines(Paths.get("logs/unicode.txt")));
            logger.info("Loaded {} unicode values from file", unicodeSet.size());
            return unicodeSet;
        } catch (IOException e) {
            logger.error("Failed to load unicode.txt", e);
            return new HashSet<>();
        }
    }

    public void checkVariantCharacters() {
        Set<String> unicodeSet = loadUnicodeSet();

        // 查询包含异体字的名字
        String firstnameSql = "select applicant_id, chi_firstname from applicant_record " +
                "where chi_firstname is not null and chi_firstname <> '' " +
                "and chi_firstname REGEXP '[^\\\\x{3400}-\\\\x{9FFF}]' " +
                "and chi_firstname REGEXP '[^\\\\x{20000}-\\\\x{323AF}]'";

        String lastnameSql = "select applicant_id, chi_lastname from applicant_record " +
                "where chi_lastname is not null and chi_lastname <> '' " +
                "and chi_lastname REGEXP '[^\\\\x{3400}-\\\\x{9FFF}]' " +
                "and chi_lastname REGEXP '[^\\\\x{20000}-\\\\x{323AF}]'";

        // 检查名
        List<Map<String, Object>> firstnameResults = jdbcTemplate.queryForList(firstnameSql);
        logger.info("Found {} records with variant characters in chi_firstname", firstnameResults.size());

        for (Map<String, Object> row : firstnameResults) {
            String applicantId = row.get("applicant_id").toString();
            String firstname = (String) row.get("chi_firstname");
            checkName(firstname, applicantId, "chi_firstname", unicodeSet);
        }

        // 检查姓
        List<Map<String, Object>> lastnameResults = jdbcTemplate.queryForList(lastnameSql);
        logger.info("Found {} records with variant characters in chi_lastname", lastnameResults.size());

        for (Map<String, Object> row : lastnameResults) {
            String applicantId = row.get("applicant_id").toString();
            String lastname = (String) row.get("chi_lastname");
            checkName(lastname, applicantId, "chi_lastname", unicodeSet);
        }
    }

    private void checkName(String name, String applicantId, String fieldName, Set<String> unicodeSet) {
        if (name == null || name.isEmpty()) {
            return;
        }

        for (char c : name.toCharArray()) {
            String hexValue = String.format("%04x", (int) c);
            if (unicodeSet.contains(hexValue)) {
                logger.info("Applicant {}: Found variant character in {}: {} (Unicode: {})",
                        applicantId, fieldName, c, hexValue);
            }
        }
    }

    public void checkVariantCharactersForApplicants(List<String> applicantIds) {
        if (applicantIds.isEmpty()) {
            return;
        }

        Set<String> unicodeSet = loadUnicodeSet();

        // 构建 IN 查询
        String idList = String.join(",", applicantIds);

        // 查询名
        String firstnameSql = "SELECT applicant_id, chi_firstname FROM applicant_record " +
                "WHERE applicant_id IN (" + idList + ") " +
                "AND chi_firstname IS NOT NULL AND chi_firstname <> '' " +
                "AND chi_firstname REGEXP '[^\\\\x{3400}-\\\\x{9FFF}]' " +
                "AND chi_firstname REGEXP '[^\\\\x{20000}-\\\\x{323AF}]'";

        List<Map<String, Object>> firstnameResults = jdbcTemplate.queryForList(firstnameSql);
        for (Map<String, Object> row : firstnameResults) {
            String applicantId = row.get("applicant_id").toString();
            String firstname = (String) row.get("chi_firstname");
            checkName(firstname, applicantId, "chi_firstname", unicodeSet);
        }

        // 查询姓
        String lastnameSql = "SELECT applicant_id, chi_lastname FROM applicant_record " +
                "WHERE applicant_id IN (" + idList + ") " +
                "AND chi_lastname IS NOT NULL AND chi_lastname <> '' " +
                "AND chi_lastname REGEXP '[^\\\\x{3400}-\\\\x{9FFF}]' " +
                "AND chi_lastname REGEXP '[^\\\\x{20000}-\\\\x{323AF}]'";

        List<Map<String, Object>> lastnameResults = jdbcTemplate.queryForList(lastnameSql);
        for (Map<String, Object> row : lastnameResults) {
            String applicantId = row.get("applicant_id").toString();
            String lastname = (String) row.get("chi_lastname");
            checkName(lastname, applicantId, "chi_lastname", unicodeSet);
        }
    }


    public boolean checkChineseNameUnicode(String firstname, String lastname, Integer applicantId, String logFile) {
        Set<String> unicodeSet = loadUnicodeSet();
        boolean hasMatch = false;
        StringBuilder nonStandardChars = new StringBuilder();

        // 获取不匹配记录的日志文件名
        String noMatchLogFile = logFile.replace(".log", "_no_match.log");

        // 获取完整的申请人信息
        String sql = "SELECT hkid, chi_firstname, chi_lastname FROM applicant_record WHERE applicant_id = ?";
        Map<String, Object> applicantInfo = jdbcTemplate.queryForMap(sql, applicantId);

        String hkid = (String) applicantInfo.get("hkid");
        String chiName = StringUtils.defaultString((String) applicantInfo.get("chi_lastname"), "") +
                StringUtils.defaultString((String) applicantInfo.get("chi_firstname"), "");

        // 检查名
        if (StringUtils.isNotBlank(firstname)) {
            for (char c : firstname.toCharArray()) {
                String hexValue = String.format("%04x", (int)c);
                if (unicodeSet.contains(hexValue)) {
                    String logMessage = String.format(
                            "Applicant %d | HKID: %s | Name: %s | Found variant character in firstname: %c (Unicode: %s)\n",
                            applicantId, hkid, chiName, c, hexValue);
                    logger.info(logMessage.trim());
                    writeToFile(logFile, logMessage);
                    hasMatch = true;
                } else if (!isStandardChineseCharacter(c)) {
                    nonStandardChars.append(String.format("%c(%s) ", c, hexValue));
                }
            }
        }

        // 检查姓
        if (StringUtils.isNotBlank(lastname)) {
            for (char c : lastname.toCharArray()) {
                String hexValue = String.format("%04x", (int)c);
                if (unicodeSet.contains(hexValue)) {
                    String logMessage = String.format(
                            "Applicant %d | HKID: %s | Name: %s | Found variant character in lastname: %c (Unicode: %s)\n",
                            applicantId, hkid, chiName, c, hexValue);
                    logger.info(logMessage.trim());
                    writeToFile(logFile, logMessage);
                    hasMatch = true;
                } else if (!isStandardChineseCharacter(c)) {
                    nonStandardChars.append(String.format("%c(%s) ", c, hexValue));
                }
            }
        }

        // 如果没有匹配，记录到不匹配日志文件
        if (!hasMatch && nonStandardChars.length() > 0) {
            String noMatchMessage = String.format(
                    "Applicant %d | HKID: %s | Name: %s | Non-standard characters found: %s\n",
                    applicantId, hkid, chiName, nonStandardChars.toString().trim());
            writeToFile(noMatchLogFile, noMatchMessage);
        }

        return hasMatch;
    }
    private boolean isStandardChineseCharacter(char c) {
        int codePoint = (int)c;
        // 检查是否在标准汉字范围内 (CJK Unified Ideographs)
        return (codePoint >= 0x3400 && codePoint <= 0x9FFF) ||  // CJK Unified Ideographs Extension A
                (codePoint >= 0x20000 && codePoint <= 0x323AF);  // CJK Unified Ideographs Extension B-G
    }


    private void writeToFile(String filePath, String content) {
        try {
            File file = new File(filePath);
            // 确保目录存在
            file.getParentFile().mkdirs();
            Files.write(
                    file.toPath(),
                    content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            logger.error("Failed to write to log file: {}", e.getMessage(), e);
        }
    }
}
