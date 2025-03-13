package com.igsl.migration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.igsl.migration.mapper.ApplicationRecordMapper;
import com.igsl.migration.model.ApplicationRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationRecordService extends ServiceImpl<ApplicationRecordMapper, ApplicationRecord> {

    @Autowired
    private ApplicationRecordMapper applicationRecordMapper;

    public Long getUnprocessedRecordsCount() {
        return applicationRecordMapper.countUnprocessedRecords();
    }

    public List<ApplicationRecord> getApplicationRecordsForUploadDoc(int limit, int offset) {
        return applicationRecordMapper.getApplicationRecordsForUploadDoc(limit, offset);
    }


    /**
     * 根据卡号列表批量查询申请记录
     * @param cardNos 卡号列表
     * @return 申请记录列表
     */
    public List<ApplicationRecord> getApplicationRecordsByCardNos(List<String> cardNos) {
        if (cardNos == null || cardNos.isEmpty()) {
            return new ArrayList<>();
        }
        return applicationRecordMapper.getApplicationRecordsByCardNos(cardNos);
    }


    public List<ApplicationRecord> getApplicationRecordsWithEproofUid(int limit, int offset) {
        return baseMapper.selectList(
                new QueryWrapper<ApplicationRecord>()
                        .isNotNull("eproof_uid")
                        .last(String.format("limit %d offset %d", limit, offset))
        );
    }
    
    /**
     * 根据eproof_uid查询申请记录
     * @param eproofUid eproof的唯一标识
     * @return 申请记录，如果不存在则返回null
     */
    public ApplicationRecord getApplicationRecordByEproofUid(String eproofUid) {
        return applicationRecordMapper.getApplicationRecordByEproofUid(eproofUid);
    }
}