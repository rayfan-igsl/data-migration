package com.igsl.migration.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * @ Description:
 * @ Author: Seven
 * @ Date: 2022/3/23  9:54
 */
@Data
public class SearchApplicant
{
    private String sex;
    private String hkid;
    private String hkidCheckDigit;
    private String engName;
    private String chiName;
    private String applicantFirstName;
    private String applicantLastName;
    private String cardNo;
    private String batchNo;
    private Date birth;
    private String displayYear;
    private String district;
    private int applicantId;
    private Date deathDate;
    private String email;
    private int applicationRecId;
    private String countryCode;
    private String phoneNum;
    private String remarks;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private String createdBy;
    private String updatedBy;
    private String cName;
    private String applicationType;
    private String applicationStatus;
    private String applicationSource;
    private LocalDateTime createdDateApplication;
    private LocalDateTime updatedDateApplication;
    private String createdByApplication;
    private String updatedByApplication;
}
