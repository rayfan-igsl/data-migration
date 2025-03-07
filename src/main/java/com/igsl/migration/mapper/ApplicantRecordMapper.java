package com.igsl.migration.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.igsl.migration.model.ApplicantRecord;
import com.igsl.migration.model.SearchApplicant;
import com.igsl.migration.model.SearchApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * Applicant information details. Mapper 接口
 * </p>
 *
 * @author Aliven
 * @since 2022-03-04
 */
public interface ApplicantRecordMapper extends BaseMapper<ApplicantRecord> {
    @Select(" Select " +
            " A.*, " +
            " B.image_path " +
            " from " +
            " applicant_record A " +
            " left join " +
            " applicant_photo B " +
            " on " +
            " A.applicant_id = B.applicant_id  " +
            " ${ew.customSqlSegment} ")
    Map<String, Object> getApplicantInfoByApplicantId(@Param(Constants.WRAPPER) QueryWrapper queryWrapper);

    @Select("SELECT * FROM applicant_record " +
            "WHERE (chi_firstname IS NOT NULL AND chi_firstname <> '' " +
            "AND chi_firstname REGEXP '[^\\\\x{3400}-\\\\x{9FFF}]' " +
            "AND chi_firstname REGEXP '[^\\\\x{20000}-\\\\x{323AF}]') " +
            "OR (chi_lastname IS NOT NULL AND chi_lastname <> '' " +
            "AND chi_lastname REGEXP '[^\\\\x{3400}-\\\\x{9FFF}]' " +
            "AND chi_lastname REGEXP '[^\\\\x{20000}-\\\\x{323AF}]') " +
            "ORDER BY applicant_id "
            /*+"LIMIT #{limit} OFFSET #{offset}"*/)
    List<ApplicantRecord> getApplicantsWithVariantChars(@Param("limit") int limit, @Param("offset") int offset);
}
