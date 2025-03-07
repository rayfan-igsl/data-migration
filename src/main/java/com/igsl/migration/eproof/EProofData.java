package com.igsl.migration.eproof;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EProofData {
	String accessToken;
	String eProofId;
	
	String eProofJson;
	String eProofJsonHash;
	
	LocalDateTime issueDateTime;
	String docUId;
	String docVersion;
	String docToken;
	
	String hkicHash;
	String hkicSdidHash;

	String issuanceDate;
}
