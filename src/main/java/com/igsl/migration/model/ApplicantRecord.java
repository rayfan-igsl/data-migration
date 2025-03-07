package com.igsl.migration.model;

import java.io.Serializable;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * Applicant information details.
 * </p>
 *
 * @author Aliven
 * @since 2022-03-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("applicant_record")
public class ApplicantRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Application record ID. System-generated sequence number.
     */
    @TableId(type = IdType.AUTO)
    private Integer applicantId;


    /**
     * Applicant address ID. Link to table APPLICANT_ADDRESS.
     */
    private Integer applicantAddressId;

    /**
     * HKID card number of the applicant. Encrypted with AES256. Partial HKID search is not applicable.
     */
    private String hkid;

    /**
     * HKID check digit.
     */
    private String hkidCheckDigit;

    /**
     * English name of the applicant.
     */
    private String engName;

    /**
     * English first name of the applicant.
     */
    private String engFirstname;

    /**
     * English last name of the applicant.
     */
    private String engLastname;

    /**
     * Chinese first name of the applicant.
     */
    private String chiName;

    /**
     * Chinese last name of the applicant.
     */
    private String chiFirstname;

    /**
     * Chinese name of the applicant.
     */
    private String chiLastname;

    /**
     * Sex of the applicant. Possible values are:M - Male  F - Female
     */
    private String sex;

    /**
     * Date of birth of the applicant
     */
    private LocalDate dob;

    /**
     * Indicate how the “dob” will be printed. Possible values are: Y – Only year part of date of birth will be printed. N – Entire date of birth will be printed.
     */
    private String displayYear;

    /**
     * Date of death of the applicant.
     */
    private LocalDate deathDate;

    /**
     * Applicant email.
     */
    private String email;

    private String countryCode;

    /**
     * Contact phone number.
     */
    private String phoneNum;

    /**
     * Full address of applicant if structured address is not applicable.
     */
    private String addressText;

    /**
     * Remarks for this record.
     */
    private String remarks;

    /**
     * Status of this applicant.
     */
    private String applicantStatus;

    /**
     * Create date and time of this record.
     */
    @TableField(value = "created_date", fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    /**
     * Create person of this record.
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /**
     * Update date and time of this record.
     */
    @TableField(value = "updated_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;

    /**
     * Update person of this record.
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /**
     * Is Card Holder.
     */
    private String isCardHolder;

    public String getHkidNumber(){
        return this.hkid.concat("(").concat(this.hkidCheckDigit).concat(")");
    }

    @TableField(exist = false)
    private String imageData;
}
