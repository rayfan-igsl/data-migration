package com.igsl.migration.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 *
 * </p>
 *
 * @author Aliven
 * @since 2022-03-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("application_record")
public class ApplicationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Application record id. System-generated sequence number.
     */
    @TableId(type = IdType.AUTO)
    private Integer applicationRecId;


    @TableField(exist=false)
    private Integer[] ids;

    /**
     * Applicant id link to table APPLICANT_RECORD.
     */
    private Integer applicantId;

    /**
     * Epaymend id link to table EPAYMENT_RECORD.
     */
    private Integer epaymentId;

    /**
     * Batch number for this application Link to table BATCH_INFO.
     */
    private String batchNo;

//    /**
//     * E-payment transaction no.
//     */
//    private String tranId;

    /**
     * Card no.
     */
    private String cardNo;

    /**
     * Current processing status of the application.(Refer to Appendix 3.2 APPLICATION STATUS)
     */
    private String applicationStatus;


    /**
     * Issue status of the application. Possible values are:F - Normal first application R - Normal re-applications SR - Special re-applications (fee-waived)
     */
    private String applicationType;

    /**
     * Receive status of the application. Possible values are:M – Mail W – Walk-in E – E-form
     */
    private String applicationSource;

    /**
     * District of the corresponding applicant’s address (for reporting)
     */
    private String districtSelect;

    /**
     * Indicate whether the application is valid or invalid. Possible values are:Y – invalid N– valid
     */
    private String invalidFlag;

    /**
     * Latest assign date and time
     */
    private LocalDateTime assignDate;

    /**
     * ID of user assigned by MO.
     */
    private String  assignedUserId;

    /**
     * Date and time of receiving the application.
     */
    private LocalDate receiveDate;

    /**
     * Date and time of card print.
     */
    private LocalDate printDate;

    /**
     * Date and time of mail card.
     */
    private LocalDate mailDate;

    /**
     * Date and time of mail virtual card.
     */
    private LocalDate emailDate;

//    /**
//     * Date and time of mail virtual card.
//     */
//    private String email;

//    /**
//     * Indicate whether fee is paid or excluded for this application. Possible values are: Y - Fee Excluded N - Fee Paid
//     */
//    private String excludeFee;

    /**
     * Cheque number of this application.
     */
    private String chequeNo;

    /**
     * Payment source.  Possible values are: -          Cheque -          Cash-          Centre-          Epayment
     */
    private String paymentMethod;

    /**
     * Fee description.
     */
    private String paymentDesc;

    /**
     * Fee receive date.
     */
    private LocalDate paymentRecDate;

    /**
     * Fee amount received.
     */
    private BigDecimal paymentAmount;

    /**
     * GMRS receipt No.
     */
    @TableField("GMRS_receipt_no")
    private String gmrsReceiptNo;

    /**
     * Payment status.
     */
    private String paymentStatus;


    /**
     * Collect Method.
     */
    @TableField("collect_method")
    private String collectMethod;

    /**
     * Other Reject Reason content.
     */
    @TableField("other_remarks")
    private String otherRemarks;

    /**
     * Apply virtual Card Or Not.
     */
    @TableField("elec_card")
    private String elecCard;

    /**
     * eform reference number.
     */
    @TableField("eform_ref_no")
    private String eformRefNo;

    /**
     * Center.
     */
    @TableField("center")
    private String center;

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

    @TableField(exist = false)
    private String email;
    @TableField(exist = false)
    private String hkid;
    @TableField(exist = false)
    private boolean cardReprinted;
    @TableField(exist = false)
    private boolean eCardPosted;
    @TableField(exist = false)
    private boolean rejectEmailSent;
    @TableField(exist = false)
    private boolean isAssigned;

    @TableField(exist = false)
    private String assignedUser;



    private String eproofJsonData;
    private String eproofHash;
    private String eproofUid;
    private String eproofVersion;
    private String eproofToken;
    private LocalDateTime eproofGenerateDatetime;



}

