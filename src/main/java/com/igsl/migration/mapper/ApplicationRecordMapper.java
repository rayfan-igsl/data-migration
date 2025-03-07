package com.igsl.migration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.igsl.migration.model.ApplicationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ApplicationRecordMapper extends BaseMapper<ApplicationRecord> {
    @Select("WITH LatestRecords AS (" +
            "  SELECT ar.*, " +
            "         ROW_NUMBER() OVER (PARTITION BY ar.applicant_id ORDER BY ar.receive_date DESC) as rn " +
            "  FROM application_record ar" +
            ") " +
            "SELECT * FROM LatestRecords " +
            "WHERE rn = 1 " +
            "  AND application_status IN ('CPR', 'CPO', 'CCL', 'MRN', 'PFP') " +
            "  AND (eproof_json_data IS NULL OR eproof_json_data = '') " +
            "  AND card_no IS NOT NULL " +
            "  AND card_no != '' " +
            "ORDER BY application_rec_id " +
            "LIMIT #{limit} OFFSET #{offset}")
        List<ApplicationRecord> getApplicationRecordsForUploadDoc(@Param("limit") int limit, @Param("offset") int offset);

    @Select("WITH LatestRecords AS (" +
            "  SELECT ar.*, " +
            "         ROW_NUMBER() OVER (PARTITION BY ar.applicant_id ORDER BY ar.receive_date DESC) as rn " +
            "  FROM application_record ar" +
            ") " +
            "SELECT COUNT(*) FROM LatestRecords " +
            "WHERE rn = 1 " +
            "  AND application_status IN ('CPR', 'CPO', 'CCL', 'MRN', 'PFP') " +
            "  AND (eproof_json_data IS NULL OR eproof_json_data = '') " +
            "  AND card_no IS NOT NULL " +
            "  AND card_no != ''")
    Long countUnprocessedRecords();

    @Select("<script>" +
            "SELECT * FROM application_record " +
            "WHERE card_no IN " +
            "<foreach collection='cardNos' item='cardNo' open='(' separator=',' close=')'>" +
            "#{cardNo}" +
            "</foreach>" +
            "</script>")
    List<ApplicationRecord> getApplicationRecordsByCardNos(@Param("cardNos") List<String> cardNos);
}