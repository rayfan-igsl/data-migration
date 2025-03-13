package com.igsl.migration;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.igsl.migration.eproof.EProofApiUtil;
import com.igsl.migration.eproof.EProofData;
import com.igsl.migration.model.ApplicantRecord;
import com.igsl.migration.model.ApplicationRecord;
import com.igsl.migration.service.ApplicantRecordService;
import com.igsl.migration.service.ApplicationRecordService;
import com.igsl.migration.tools.GetDateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class DataMigration {

    @Autowired
    private ApplicationRecordService applicationRecordService;

    @Autowired
    private ApplicantRecordService applicantRecordService;

    private int batchSize = 3000;
    private int batch = 180;
    private int threadCount = 30;

    private int offset = 0;
    private ExecutorService executorService;
    private final StringBuilder failedRecords = new StringBuilder();
    private String errorLogFile;

    boolean fileMode = false;
    boolean revokeMode = false;

    // test only!!!!!!!!!!!!!!
    // private String performanceLogFile;

    public void execute(String[] args) {
        // 处理参数
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg.startsWith("--batchSize=")) {
                    batchSize = Integer.parseInt(arg.substring("--batchSize=".length()));
                } else if (arg.startsWith("--batch=")) {
                    batch = Integer.parseInt(arg.substring("--batch=".length()));
                } else if (arg.startsWith("--threads=")) {
                    threadCount = Integer.parseInt(arg.substring("--threads=".length()));
                } else if (arg.startsWith("--offset=")) {
                    offset = Integer.parseInt(arg.substring("--offset=".length()));
                } else if (arg.startsWith("--fileMode=")) {
                    fileMode = "y".equalsIgnoreCase(arg.substring("--fileMode=".length()));
                    if (fileMode) {
                        // When in file mode, set batch and threadCount to 1
                        batch = 1;
                        threadCount = 1;
                        log.info("File mode enabled: Setting batch=1 and threadCount=1");
                    }
                } else if (arg.startsWith("--revoke=")) {
                    revokeMode = "y".equalsIgnoreCase(arg.substring("--revoke=".length()));
                }
            }
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        errorLogFile = String.format("logs/migration_error_%s.log", timestamp);

        // test only!!!!!!!!!!!!!!
        // performanceLogFile = String.format("logs/performance_%s.log", timestamp);

        log.info("Begin Task：batchSize={}, batch={}, threads={}, offset={}", batchSize, batch, threadCount, offset);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Long totalUnprocessedCount = 0L;
        if (!fileMode && !revokeMode) {
            totalUnprocessedCount = applicationRecordService.getUnprocessedRecordsCount();
            String startLog = String.format(
                    "\n-------------------" +
                            "Task begin time：%s  " +
                            "batchSize=%d, batches=%d, threads=%d, records to process：%d" +
                            "-------------------\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    batchSize,
                    batch,
                    threadCount,
                    totalUnprocessedCount);
            log.info(startLog);
            writeToFile("logs/batch_execution_time.log", startLog);
            writeToFile(errorLogFile, startLog);
        }

        Long initialCount = totalUnprocessedCount;
        BatchStats grandTotalStats = new BatchStats();

        if (revokeMode) {
            executeRevoke();
            return;
        }

        for (int batchIndex = 0; batchIndex < batch; batchIndex++) {
            StopWatch batchStopWatch = new StopWatch();
            batchStopWatch.start();
            LocalDateTime batchStartTime = LocalDateTime.now();

            try {
                // Initialize or refresh token at the start of each batch
                String token = EProofApiUtil.initializeToken();
                log.info("Successfully initialized/refreshed token for batch {}", batchIndex + 1);
                // 一次性查询所有记录
                // 根据模式选择获取记录的方式
                List<ApplicationRecord> allRecords;
                if (fileMode) {
                    allRecords = getRecordFromCardNoFile("logs/card_no.txt");
                    if (allRecords.isEmpty()) {
                        String emptyLog = "File mode: No records to process\n";
                        log.info(emptyLog);
                        writeToFile("logs/batch_execution_time.log", emptyLog);
                        return;
                    }
                    log.info("File mode: Get {} records from file", allRecords.size());
                } else {
                    allRecords = applicationRecordService.getApplicationRecordsForUploadDoc(batchSize, offset);
                    if (allRecords.isEmpty()) {
                        String emptyLog = "DB mode: No records to process\n";
                        log.info(emptyLog);
                        writeToFile("logs/batch_execution_time.log", emptyLog);
                        return;
                    }
                }

                // 根据 applicationRecId 分配记录给不同线程
                List<List<ApplicationRecord>> threadRecords = new ArrayList<>(threadCount);
                for (int i = 0; i < threadCount; i++) {
                    threadRecords.add(new ArrayList<>());
                }

                // 根据 ID 取模分配记录
                for (ApplicationRecord record : allRecords) {
                    int threadIndex = (int) (record.getApplicationRecId() % threadCount);
                    threadRecords.get(threadIndex).add(record);
                }

                // 创建线程池并提交任务
                executorService = Executors.newFixedThreadPool(threadCount);
                List<Future<BatchStats>> futures = new ArrayList<>();

                for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
                    List<ApplicationRecord> recordsForThread = threadRecords.get(threadIndex);
                    if (!recordsForThread.isEmpty()) {
                        Future<BatchStats> future = executorService.submit(() -> processRecordsBatch(recordsForThread));
                        futures.add(future);
                    }
                }

                // 收集处理结果
                BatchStats batchStats = new BatchStats();
                for (Future<BatchStats> future : futures) {
                    try {
                        BatchStats stats = future.get();
                        batchStats.successCount += stats.successCount;
                        batchStats.failureCount += stats.failureCount;
                    } catch (InterruptedException | ExecutionException e) {
                        log.error("Thread execution error", e);
                    }
                }

                executorService.shutdown();
                batchStopWatch.stop();
                LocalDateTime batchEndTime = LocalDateTime.now();
                // 更新总计数据
                grandTotalStats.successCount += batchStats.successCount;
                grandTotalStats.failureCount += batchStats.failureCount;
                double batchTimeInSeconds = batchStopWatch.getTotalTimeMillis() / 1000.0;

                // 记录批次执行结果
                String batchLog = String.format(
                        "Batch %d: process %d records, begin time: %s, end time: %s, consume time: %.3f seconds\n" +
                                "    Success: %d records\n" +
                                "    Failure: %d records\n",
                        batchIndex + 1,
                        allRecords.size(),
                        batchStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                        batchEndTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                        batchTimeInSeconds,
                        batchStats.successCount,
                        batchStats.failureCount);
                log.info(batchLog);
                writeToFile("logs/batch_execution_time.log", batchLog);
            } catch (Exception e) {
                log.error("Failed to initialize token for batch " + (batchIndex + 1), e);
                continue; // Skip current batch
            }
        }

        stopWatch.stop();
        long totalSeconds = stopWatch.getTotalTimeMillis() / 1000; // 转换为秒

        String finalLog;
        if (fileMode) {
            finalLog = String.format(
                    "------------------------------------------------------------------------------------------------------------------\n"
                            +
                            "Task complete time：%s Total consume: %d seconds\n" +
                            "Total records %d，Success: %d，Fail:%d。\n" +
                            "------------------------------------------------------------------------------------------------------------------\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    totalSeconds,
                    grandTotalStats.successCount + grandTotalStats.failureCount,
                    grandTotalStats.successCount,
                    grandTotalStats.failureCount);
        } else {
            // 记录最终执行结果
            Long currentUnprocessedCount = applicationRecordService.getUnprocessedRecordsCount();
            Long theoreticalRemaining = initialCount - grandTotalStats.successCount;
            finalLog = String.format(
                    "------------------------------------------------------------------------------------------------------------------\n"
                            +
                            "Task complete time：%s Total consume: %d seconds\n" +
                            "Total records %d，Success: %d，Fail:%d。\n" +
                            "Theoretical remaining records to process: %d\n" +
                            "Actual remaining records to process: %d\n" +
                            "Difference: %d\n" +
                            "------------------------------------------------------------------------------------------------------------------\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    totalSeconds,
                    grandTotalStats.successCount + grandTotalStats.failureCount,
                    grandTotalStats.successCount,
                    grandTotalStats.failureCount,
                    theoreticalRemaining,
                    currentUnprocessedCount,
                    currentUnprocessedCount - theoreticalRemaining);
        }

        log.info(finalLog);
        writeToFile("logs/batch_execution_time.log", finalLog);
        // 添加任务结束日志到错误日志
        String endLog = String.format(
                "-----------------------------------------------------------------------------------------------------------\n"
                        +
                        "Task end time: %s with %d failed records\n" +
                        "-----------------------------------------------------------------------------------------------------------\n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                grandTotalStats.failureCount);
        writeToFile(errorLogFile, endLog);
    }

    private void executeRevoke() {
        log.info("Starting revocation process from file");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            // Initialize token at the start
            String token = EProofApiUtil.initializeToken();
            log.info("Successfully initialized token for revocation");

            // 从文件读取UID列表
            List<String> uidList = getUidListFromFile("logs/revokeList.txt");
            if (uidList.isEmpty()) {
                log.info("No UIDs to revoke found in the file");
                return;
            }
            
            log.info("Found {} UIDs to revoke in file", uidList.size());
            
            BatchStats grandTotalStats = new BatchStats();
            
            // 单线程处理所有UID
            for (String uid : uidList) {
                try {
                    EProofData config = new EProofData();
                    config.setDocUId(uid);
                    
                    // 先执行revoke操作
                    if (EProofApiUtil.revokeDoc(config)) {
                        log.info("Successfully revoked document with UID: {}", uid);
                        grandTotalStats.successCount++;
                        
                        // 查询对应的ApplicationRecord
                        ApplicationRecord record = applicationRecordService.getApplicationRecordByEproofUid(uid);
                        
                        if (record != null) {
                            // update record
                            LambdaUpdateWrapper<ApplicationRecord> updateWrapper = new LambdaUpdateWrapper<>();
                            updateWrapper.eq(ApplicationRecord::getApplicationRecId, record.getApplicationRecId())
                                    .set(ApplicationRecord::getEproofJsonData, "C")
                                    .set(ApplicationRecord::getEproofHash, null)
                                    .set(ApplicationRecord::getEproofUid, null)
                                    .set(ApplicationRecord::getEproofVersion, null)
                                    .set(ApplicationRecord::getEproofToken, null)
                                    .set(ApplicationRecord::getEproofGenerateDatetime, null);
                            
                            applicationRecordService.update(updateWrapper);
                            log.info("Updated application record with ID: {}", record.getApplicationRecId());
                        } else {
                            log.warn("No application record found for UID: {}", uid);
                        }
                    } else {
                        grandTotalStats.failureCount++;
                        String errorMsg = String.format("Failed to revoke document with UID: %s\n", uid);
                        writeToFile(errorLogFile, errorMsg);
                        log.error(errorMsg.trim());
                    }
                } catch (Exception e) {
                    grandTotalStats.failureCount++;
                    String errorMsg = String.format("Error processing UID: %s, Error: %s\n", uid, e.getMessage());
                    writeToFile(errorLogFile, errorMsg);
                    log.error(errorMsg.trim());
                }
            }
            
            stopWatch.stop();
            long totalSeconds = stopWatch.getTotalTimeMillis() / 1000;

            String finalLog = String.format(
                    "--------------------------------------------------\n" +
                            "Revocation from file complete time: %s\n" +
                            "Total UIDs processed: %d\n" +
                            "Success: %d\n" +
                            "Failed: %d\n" +
                            "Total time: %d seconds\n" +
                            "--------------------------------------------------\n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    grandTotalStats.successCount + grandTotalStats.failureCount,
                    grandTotalStats.successCount,
                    grandTotalStats.failureCount,
                    totalSeconds);

            log.info(finalLog);
            writeToFile("logs/batch_execution_time.log", finalLog);

        } catch (Exception e) {
            log.error("Error during revocation process", e);
        }
    }

    /*private BatchStats processRevokeRecordsBatch(List<ApplicationRecord> records) {
        BatchStats batchStats = new BatchStats();
        if (records.isEmpty()) {
            return batchStats;
        }

        for (ApplicationRecord record : records) {
            try {
                EProofData config = new EProofData();
                config.setDocUId(record.getEproofUid());

                if (EProofApiUtil.revokeDoc(config)) {
                    // 使用LambdaUpdateWrapper更新记录
                    LambdaUpdateWrapper<ApplicationRecord> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(ApplicationRecord::getApplicationRecId, record.getApplicationRecId())
                            .set(ApplicationRecord::getEproofJsonData, null)
                            .set(ApplicationRecord::getEproofHash, null)
                            .set(ApplicationRecord::getEproofUid, null)
                            .set(ApplicationRecord::getEproofVersion, null)
                            .set(ApplicationRecord::getEproofToken, null)
                            .set(ApplicationRecord::getEproofGenerateDatetime, null);

                    applicationRecordService.update(updateWrapper);
                    batchStats.successCount++;
                } else {
                    batchStats.failureCount++;
                    String errorMsg = String.format("Failed to revoke document for record ID: %d, HKID: %s\n",
                            record.getApplicationRecId(), record.getHkid());
                    writeToFile(errorLogFile, errorMsg);
                    log.error(errorMsg.trim());
                }
            } catch (Exception e) {
                batchStats.failureCount++;
                logMigrationError(record, null, e);
            }
        }

        return batchStats;
    }*/

    private BatchStats processRecordsBatch(List<ApplicationRecord> records) {
        BatchStats batchStats = new BatchStats();
        if (records.isEmpty()) {
            return batchStats;
        }

        List<ApplicationRecord> updateBatch = new ArrayList<>();
        for (ApplicationRecord item : records) {
            ApplicantRecord appc = null;
            try {

                EProofData config = new EProofData();

                // issuanceDate
                if (item.getPrintDate() == null)
                    config.setIssuanceDate(GetDateUtils.getDateTimeUTCString(item.getCreatedDate()));
                else
                    config.setIssuanceDate(GetDateUtils.getDateTimeUTCString(item.getPrintDate().atStartOfDay()));

                // 1.gen json doc
                appc = applicantRecordService.getById(item.getApplicantId());
                appc.setImageData(applicantRecordService.getPhotoBase64ImgTag(item.getApplicantId()));

                // hkid
                item.setHkid(appc.getHkid());

                // 2.gen signed json doc
                if (EProofApiUtil.generateJsonDoc(config, item, appc)) {
                    EProofApiUtil.constructDoc4Issue(config, appc);

                    if (StringUtils.isNotBlank(item.getEproofUid())) {
                        config.setDocUId(item.getEproofUid());
                    }

                    // 3.issue doc

                    // test only!!!!!!!!!
                    /*
                     * StopWatch issueWatch = new StopWatch("IssueDoc");
                     * issueWatch.start("IssueDocTask");
                     */
                    boolean issueResult = EProofApiUtil.issueDoc(config);
                    /*
                     * issueWatch.stop();
                     * double issueTimeInSeconds = issueWatch.getTotalTimeMillis() / 1000.0;
                     */

                    // if (EProofApiUtil.issueDoc(config)) {
                    // test only!!!!!!!!!!!
                    if (!issueResult) {
                        throw new Exception();
                    } else {
                        // 4.upload json to intermediate server

                        // test only!!!!!!!!!!!
                        /*
                         * StopWatch uploadWatch = new StopWatch("Upload");
                         * uploadWatch.start("UploadTask");
                         */
                        boolean uploadResult = EProofApiUtil.upload2Intermediate(config);
                        /*
                         * uploadWatch.stop();
                         * double uploadTimeInSeconds = uploadWatch.getTotalTimeMillis() / 1000.0;
                         */

                        // 记录执行时间到性能日志
                        /*
                         * String timeLog = String.format(
                         * "%s - Performance Log - Application Record ID: %d, HKID: %s\n" +
                         * "    Issue Document Time: %.3f seconds\n" +
                         * "    Upload to Intermediate Time: %.3f seconds\n" +
                         * "    Total API Time: %.3f seconds\n",
                         * LocalDateTime.now().format(DateTimeFormatter.
                         * ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
                         * item.getApplicationRecId(),
                         * item.getHkid(),
                         * issueTimeInSeconds,
                         * uploadTimeInSeconds,
                         * issueTimeInSeconds + uploadTimeInSeconds);
                         * writeToFile(performanceLogFile, timeLog);
                         * log.info(timeLog.trim());
                         */

                        // if (EProofApiUtil.upload2Intermediate(config)) {

                        // test only!!!!!!!!!!!!!
                        if (uploadResult) {

                            // 5. update application record
                            item.setEproofGenerateDatetime(config.getIssueDateTime());
                            item.setEproofHash(config.getEProofJsonHash());
                            // item.setEproofJsonData(config.getEProofJson());
                            item.setEproofJsonData("Y");
                            item.setEproofUid(config.getDocUId());
                            item.setEproofVersion(config.getDocVersion());
                            item.setEproofToken(config.getDocToken());
                            // item.setApplicationStatus("CPO"); do not change status in migration
                            // record!!!!!!!!!!!!!!!!!!!
                            item.setElecCard("Y");

                            updateBatch.add(item);
                            batchStats.successCount++;

                        }
                    }
                }
            } catch (Exception e) {
                batchStats.failureCount++;
                logMigrationError(item, appc, e);
                log.error("E-Card processing failed, HKID:{}, error:{}", item.getHkid(), e.getMessage());
                synchronized (failedRecords) {
                    failedRecords.append(item.getHkid()).append(": ").append(e.getMessage()).append(",<br>");
                }
            }
        }

        // 批量更新
        if (!updateBatch.isEmpty()) {
            applicationRecordService.updateBatchById(updateBatch);
        }

        return batchStats;
    }

    private void logMigrationError(ApplicationRecord appRecord, ApplicantRecord applicantRecord, Exception e) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

            // 构建错误消息
            String errorMessage = String.format(
                    "%s - Migration Error - Application Record ID: %d, HKID: %s, Card No: %s, Applicant ID: %d, Error: %s\n",
                    timestamp,
                    appRecord.getApplicationRecId(),
                    appRecord.getHkid(),
                    appRecord.getCardNo(),
                    applicantRecord != null ? applicantRecord.getApplicantId() : 0,
                    e.getMessage());

            // 获取完整的堆栈跟踪
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stackTrace = String.format("%s - Stack trace: \n%s\n", timestamp, sw.toString());

            // 写入错误日志文件
            writeToFile(errorLogFile, errorMessage);
            writeToFile(errorLogFile, stackTrace);

            // 同时输出到控制台
            log.error(errorMessage.trim());
            log.error("Stack trace: \n{}", sw.toString());
        } catch (Exception ex) {
            log.error("Error while logging: " + ex.getMessage(), ex);
        }
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
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write log", e);
        }
    }

    private static class BatchStats {
        int successCount = 0;
        int failureCount = 0;
    }

    /**
     * 从文件中读取卡号并获取对应的applicationRecords
     * 
     * @param filename 文件名
     * @return list applicationReacords
     */
    private List<ApplicationRecord> getRecordFromCardNoFile(String filename) {
        List<String> cardNos = new ArrayList<>();

        try {
            // 直接从文件系统读取文件
            File file = new File(filename);
            if (!file.exists()) {
                log.error("File does not exist: {}", filename);
                return new ArrayList<>();
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 去除空白字符
                    String cardNo = line.trim();
                    if (!cardNo.isEmpty()) {
                        cardNos.add(cardNo);
                    }
                }
            }

            if (cardNos.isEmpty()) {
                log.warn("No valid card numbers found in file {}", filename);
                return new ArrayList<>();
            }

            log.info("Successfully read {} card numbers from file {}", cardNos.size(), filename);

            List<ApplicationRecord> records = applicationRecordService.getApplicationRecordsByCardNos(cardNos);
            log.info("Successfully queried {} application records", records.size());

            if (records.size() != cardNos.size()) {
                log.warn("cardNos: {}, records: {}",
                        cardNos.size(), records.size());
            }

            return records;

        } catch (IOException e) {
            log.error("Failed to read file: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 从文件中读取UID列表
     * 
     * @param filename 文件名
     * @return UID列表
     */
    private List<String> getUidListFromFile(String filename) {
        List<String> uidList = new ArrayList<>();

        try {
            // 直接从文件系统读取文件
            File file = new File(filename);
            if (!file.exists()) {
                log.error("File does not exist: {}", filename);
                return new ArrayList<>();
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String uid = line.trim();
                    if (!uid.isEmpty()) {
                        uidList.add(uid);
                    }
                }
            }

            if (uidList.isEmpty()) {
                log.warn("No valid UIDs found in file {}", filename);
                return new ArrayList<>();
            }

            log.info("Successfully read {} UIDs from file {}", uidList.size(), filename);
            return uidList;

        } catch (IOException e) {
            log.error("Failed to read file: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}