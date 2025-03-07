package com.igsl.migration.eproof;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.igsl.migration.model.ApplicantRecord;
import com.igsl.migration.model.ApplicationRecord;
import com.igsl.migration.tools.EproofCharMapUtils;
import com.igsl.migration.tools.GetDateUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Configuration
public class EProofApiUtil {
	private static final Logger logger = LogManager.getLogger(EProofApiUtil.class);

	private static final LocalDateTime publishDt = LocalDateTime.of(2699, 12, 31, 23, 59, 59);


	@Value("${eproof.host}")
	private String host;
	private static String eProofHost;

	@Value("${eproof.clientId}")
	private String clientId;
	private static String eProofClientId;

	@Value("${eproof.clientSecret}")
	private String clientSecret;
	private static String eProofClientSecret;

	@Value("${eproof.privateKey}")
	private String privateKey;
	private static String eProofPrivateKey;

	@Value("${eproof.did}")
	private String did;
	private static String eProofDid;

	@Value("${eproof.didKey}")
	private String didKey;
	private static String eProofDidKey;

	@Value("${eproof.docTypeId}")
	private String docTypeId;
	private static String eProofDocTypeId;

	@Value("${eproof.templateCode}")
	private String templateCode;
	private static String eProofTemplateCode;

	@Value("${eproof.hkicSalt}")
	private String hkicSalt;
	private static String eProofHkicSalt;

	@Value("${eproof.hkicSaltId}")
	private String hkicSaltId;
	private static String eProofHkicSaltId;

	@Value("${eproof.sdid}")
	private String sdid;
	private static String eProofSdid;

	@Value("${eproof.dataUrl}")
	private String dataUrl;
	private static String eProofDataUrl;

	private static String cachedAccessToken;
	private static LocalDateTime tokenExpiryTime;
	private static final int TOKEN_REFRESH_WINDOW = 50; // Token validate in 50 minutes

	private static final ObjectMapper objectMapper = new ObjectMapper();

	// Initialize or refresh token
	public static String initializeToken() throws Exception {
		if (isTokenValid()) {
			return cachedAccessToken;
		}

		logger.debug("[e-Proof] Get Access Token - Start");
		String url = eProofHost + "/authToken";

		HashMap<String, String> jsonMap = new HashMap<>();
		jsonMap.put("grantType", "clientCredentials");
		jsonMap.put("clientId", eProofClientId);
		jsonMap.put("clientSecret", eProofClientSecret);

		ObjectMapper omapper = new ObjectMapper();
		String jsonData = omapper.writeValueAsString(jsonMap);

		String response = HttpUtil.post(url, jsonData);

		JSONObject jsonObj = JSONObject.parseObject(response);
		if (jsonObj.get("status") != null && jsonObj.get("status").equals("Successful")) {
			JSONObject dataObj = JSONObject.parseObject(String.valueOf(jsonObj.get("data")));
			String newToken = dataObj.get("accessToken").toString();

			// Update cached token and expiry time
			cachedAccessToken = newToken;
			tokenExpiryTime = LocalDateTime.now().plusMinutes(TOKEN_REFRESH_WINDOW);

			logger.debug("[e-Proof] Get Access Token - Success");
			return newToken;
		} else {
			logger.debug("[e-Proof] Get Access Token - Failed");
			throw new RuntimeException("Failed to get access token");
		}
	}

	// Check if current token is still valid
	private static boolean isTokenValid() {
		if (cachedAccessToken == null || tokenExpiryTime == null) {
			return false;
		}
		return LocalDateTime.now().isBefore(tokenExpiryTime);
	}

	@PostConstruct
	public void transValues() {
		eProofHost = this.host;
		eProofClientId = this.clientId;
		eProofClientSecret = this.clientSecret;
		eProofPrivateKey = this.privateKey;
		eProofDid = did;
		eProofDidKey = didKey;
		eProofDocTypeId = docTypeId;
		eProofTemplateCode = templateCode;
		eProofHkicSalt = hkicSalt;
		eProofHkicSaltId = hkicSaltId;
		eProofSdid = sdid;
		eProofDataUrl = dataUrl;

		objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
	}

	// Get access token from cache
	public static Boolean getAccessTokenByClientCredentials(EProofData config) throws Exception {
		if (cachedAccessToken != null) {
			config.setAccessToken(cachedAccessToken);
			return true;
		}
		return false;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Boolean generateJsonDoc(EProofData config, ApplicationRecord applRec, ApplicantRecord appcRec) throws Exception {
		if (!getAccessTokenByClientCredentials(config)) {
			return false;
		}

		String cardNo = applRec.getCardNo();

		String dob = appcRec.getDisplayYear().equals("Y") ? appcRec.getDob().format(DateTimeFormatter.ofPattern("yyyy")) : appcRec.getDob().format(DateTimeFormatter.ofPattern("dd-MM"));
		String day = appcRec.getDob().format(DateTimeFormatter.ofPattern("dd"));
		String mth = appcRec.getDob().format(DateTimeFormatter.ofPattern("MM"));
		String dobString="";
		if (appcRec.getDisplayYear().equals("Y")){
			dobString="<div style='display: inline-block;text-align: center; margin-left: 8%;'>"
					+dob
					+"<br /><span>Y/</span><span style='font-family: MingLiU;'>&#x5e74;</span></div>";
		}else {

			dobString="<div style='display: inline-block; text-align: center; margin-right: 5%;'>"
					+day
					+"<br /><span>D/</span><span style='font-family: MingLiU'>&#x65E5;</span></div><div style='display: inline-block;text-align: center;'>"
					+mth
					+"<br /><span>M/</span><span style='font-family: MingLiU'>&#x6708;</span></div>";
		}

		// Process English name
		String engName = StringUtils.defaultString(appcRec.getEngLastname(), "").trim() +
				(StringUtils.isNotBlank(appcRec.getEngLastname()) ? ", " : "") +
				StringUtils.defaultString(appcRec.getEngFirstname(), "").trim();
		if (StringUtils.isBlank(engName)) {
			throw new IllegalArgumentException("English name is null or empty");
		}

		String nameEnLine1 = "";
		String nameEnLine2 = "";
		if (engName.length() <= 25) {
			nameEnLine1 = engName;
		} else {
			String[] nameSplit = engName.split(" ");
			String tempLine = "";
			for (int i = 0; i < nameSplit.length; i++) {
				if (tempLine.equals("")) { // first word
					tempLine += nameSplit[i];
				} else {
					if ((tempLine + " " + nameSplit[i]).length() <= 25) { // if line <=25 after adding next word, add
						// next word to line
						tempLine += " " + nameSplit[i];
					} else if (nameEnLine1.equals("")) { // if line + next word > 25 and it is the first line, set the
						// 1st line and set 2nd line
						nameEnLine1 = tempLine;
						tempLine = nameSplit[i];
					} else if (nameEnLine2.equals("")) { // if line + next word > 25 and it is 2nd line, set the 2nd
						// line and skip other
						nameEnLine2 = tempLine;
						break;
					}
				}
			}
			if (nameEnLine1.equals("")) { // set name if not reach 25 char
				nameEnLine1 = tempLine;
			} else if (nameEnLine2.equals("")) { // set name if not reach 25 char
				nameEnLine2 = tempLine;
			}
		}


		// Process Chinese name
		String chiName = StringUtils.defaultString(appcRec.getChiLastname(), "").trim() +
				StringUtils.defaultString(appcRec.getChiFirstname(), "").trim();

		Map<String, String> charMap = EproofCharMapUtils.getDataMap();

		String nameTag = "";
		if(chiName!=null && !chiName.equals("")){  // Chinese name not empty
			StringBuilder result = new StringBuilder();
			for (char c : chiName.toCharArray()) {
				String charHex = Integer.toHexString(c);
				if (charMap.containsKey(charHex)) {
					result.append(charMap.get(charHex)).append(" ");
				} else {
					result.append(c).append(" ");
				}
				//String uHex = "&#x".concat(charHex).concat(";");
				//result.append(uHex).append(" ");
			}
			chiName = result.toString().trim();
			nameTag="<div id='li_personCNName' style='font-weight: bold; font-family: MingLiU; padding-top: 0.5%;'>"
					+chiName
					+"</div>"
					+"<div id='li_personENName' style='font-weight: bold; font-family: Times; padding-top: 3.4%; line-height: 1.01; max-width: 55%; overflow-wrap: break-word; white-space: pre-wrap;'>"
					+engName
					+"</div>";
		} else {
			if(nameEnLine2.equals("")){   //no Chinese name and only 1 line English name
				nameTag="<div id='li_personCNName' style='font-weight: bold; font-family: Times; padding-top: 0.5%;'>"
						+engName
						+"</div>";
			} else {  //no Chinese name and 2 line English name
				nameTag="<div id='li_personCNName' style='font-weight: bold; font-family: Times; padding-top: 0.5%;'>"
						+nameEnLine1
						+"</div>"
						+"<div id='li_personENName' style='font-weight: bold; font-family: Times; padding-top: 3.5%; max-width: 55%; overflow-wrap: break-word; white-space: pre-wrap;'>"
						+nameEnLine2
						+"</div>";
			}


		}


		String sex = "M".equals(appcRec.getSex()) ? "男 Male"
				: "F".equals(appcRec.getSex()) ? "女 Female"
				: appcRec.getSex();

		String imageData = appcRec.getImageData();

		String eProofId = "sced_".concat(cardNo);
		config.setEProofId(eProofId);

		Map jsonMap = new TreeMap<>();
		jsonMap.put("en_mobile_card_line1", cardNo);
		jsonMap.put("en_mobile_card_line2", engName);
		jsonMap.put("en_mobile_card_line3", dob);
		jsonMap.put("tc_mobile_card_line1", cardNo);
		jsonMap.put("tc_mobile_card_line2", chiName);
		jsonMap.put("tc_mobile_card_line3", dob);
		jsonMap.put("sc_mobile_card_line1", cardNo);
		jsonMap.put("sc_mobile_card_line2", chiName);
		jsonMap.put("sc_mobile_card_line3", dob);

		jsonMap.put("ChineseName",chiName);
		jsonMap.put("EnglishName",engName);
		jsonMap.put("nameTag",nameTag);
		jsonMap.put("cardNo",cardNo);
		jsonMap.put("sex",sex);
		jsonMap.put("day",day);
		jsonMap.put("mth",mth);
		jsonMap.put("DOB",dobString);
		jsonMap.put("imageData",imageData);

		jsonMap.put("eproof_id", eProofId);
		jsonMap.put("template_code", eProofTemplateCode);

//		jsonMap.put("issue_date", GetDateUtils.getDateTimeUTCString(null));
		jsonMap.put("issue_date", config.getIssuanceDate());
		jsonMap.put("expire_date", "");
		jsonMap.put("type", "personal");
		jsonMap.put("schema", "1.0");

        /*String oriJson = JSONObject.toJSONString(jsonMap);
		config.setEProofJson(oriJson);*/

		String oriJson = objectMapper.writeValueAsString(jsonMap);
		config.setEProofJson(oriJson);

		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void constructDoc4Issue(EProofData config, ApplicantRecord appc) throws Exception {
		String hkicHash = computeHash(appc.getHkid().concat(eProofHkicSalt));
		config.setHkicHash(hkicHash);

		//------------------------should be HKID+SDID instead of eProofTemplateCode ???????---------------------------------------
		String hkicSdidHash = computeHash(appc.getHkid().concat(eProofSdid));
		config.setHkicSdidHash(hkicSdidHash);

		// construct eproof json body
		Map systemJsonMap = new TreeMap<>();
		systemJsonMap.put("hkicHash", hkicHash);
		systemJsonMap.put("expirationDate", "");
		systemJsonMap.put("hkicSaltId", eProofHkicSaltId);

		Map<String, String> oriJsonMap = new ObjectMapper().readValue(config.getEProofJson(), HashMap.class);

		Map credentialSubjectJsonMap = new TreeMap<>();
		credentialSubjectJsonMap.put("display", oriJsonMap);
		credentialSubjectJsonMap.put("system", systemJsonMap);
		credentialSubjectJsonMap.put("version", "1.0");

		Map vcJsonMap = new TreeMap<>();
		vcJsonMap.put("@context", new ArrayList<>(Arrays.asList("https://www.w3.org/2018/credentials/v1")));
		vcJsonMap.put("type", new ArrayList<>(Arrays.asList("VerifiableCredential")));
//		vcJsonMap.put("version", "1.0");
		vcJsonMap.put("issuer", eProofDid);
//		vcJsonMap.put("issuanceDate", GetDateUtils.getDateTimeUTCString(null));
		vcJsonMap.put("issuanceDate", config.getIssuanceDate());
		vcJsonMap.put("credentialSubject", credentialSubjectJsonMap);

		// vc proof
        Map vcProofJsonMap = new TreeMap<>();
        vcProofJsonMap.put(  "type", "SHA256withRSA");
        vcProofJsonMap.put(  "created", GetDateUtils.getDateTimeUTCString(null));
        vcProofJsonMap.put(  "verificationMethod", eProofDid.concat("#").concat(eProofDidKey));
        vcProofJsonMap.put(  "proofPurpose", "assertionMethod");
        
        // sign eproof json body
		String proofValue = signatureWithPrivateKey(config, JSONObject.toJSONString(vcJsonMap));
		vcProofJsonMap.put(  "proofValue", proofValue);
        
        // append vc proof to eproof json body
        vcJsonMap.put("proof", vcProofJsonMap);
        
        // compute json hash
//        String vcString = JSONObject.toJSONString(vcJsonMap);
		String vcString = objectMapper.writeValueAsString(vcJsonMap);

        String vcBase64Hash = computeHash(vcString);
        
        config.setEProofJson(vcString);
        config.setEProofJsonHash(vcBase64Hash);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Boolean issueDoc(EProofData config) throws Exception {
		logger.debug("[e-Proof] Isse Document - Start");
		if (!getAccessTokenByClientCredentials(config)) {
			return false;
		}

		LocalDateTime nowDt = LocalDateTime.now();
		config.setIssueDateTime(nowDt);

		HashMap requestBodyJsonMap = new HashMap<>();
        requestBodyJsonMap.put("eProofId", config.getEProofId());
        requestBodyJsonMap.put("eProofTypeId", eProofDocTypeId);
        requestBodyJsonMap.put("templateCode", eProofTemplateCode);        
        requestBodyJsonMap.put("hkicHash", config.getHkicHash());        
        requestBodyJsonMap.put("hkicSaltId", eProofHkicSaltId);
        requestBodyJsonMap.put("sdid", config.getHkicSdidHash());
//        requestBodyJsonMap.put("issuanceDate", GetDateUtils.getDateTimeUTCString(nowDt));
		requestBodyJsonMap.put("issuanceDate", config.getIssuanceDate());

//		requestBodyJsonMap.put("publishDate", GetDateUtils.getDateTimeUTCString(publishDt) );
        requestBodyJsonMap.put("dataHash", config.getEProofJsonHash());
        requestBodyJsonMap.put("authMethod", "02");
        requestBodyJsonMap.put("walletOption", "03");
        requestBodyJsonMap.put("dataUrl", eProofDataUrl);

        if (!StringUtils.isAllBlank(config.getDocUId())) {
			requestBodyJsonMap.put("id", config.getDocUId());
        }
        
        String reqBody = JSONObject.toJSONString(requestBodyJsonMap);
		HttpResponse response = HttpRequest.post(eProofHost + "/eProofMetadata").bearerAuth(config.getAccessToken()).body(reqBody).execute();


		JSONObject jsonObj = JSONObject.parseObject(response.body());

		//should commet it!!!!!!!!!!!!!!!!!!!!!!!!!!!!
/*		System.out.println("/eProofMetadata response========="+response.body());
		System.out.println("status============"+jsonObj.get("status"));*/
//		writeToFile("logs/response.log", response.body()+"\n");
//		System.out.println("==================response============="+response.body());
		if (jsonObj.get("status") != null && jsonObj.get("status").equals("Successful")) {
			JSONObject dataObj = JSONObject.parseObject(String.valueOf(jsonObj.get("data")));

			config.setDocUId(dataObj.get("id").toString());
			config.setDocVersion(dataObj.get("version").toString());
			config.setDocToken(dataObj.get("token").toString());


			logger.debug("[e-Proof] Isse Document - Success");
			return true;
		} else {
			logger.debug("[e-Proof] Isse Document - Failed");
			return false;
		}
	}


	public static Boolean upload2Intermediate(EProofData config) throws JsonProcessingException {
		Map<String, String> jsonMap = new TreeMap<>();
		jsonMap.put(  "uid", config.getDocUId());
		jsonMap.put(  "json", config.getEProofJson());
        
        ObjectMapper omapper = new ObjectMapper();
		String composedJson = omapper.writeValueAsString(jsonMap);

		String response = HttpUtil.post(eProofHost + "/upload", composedJson);

		JSONObject jsonObj = JSONObject.parseObject(response);

		if (jsonObj.get("status") != null && jsonObj.get("status").toString().equalsIgnoreCase("successful")) {
			logger.debug("Upload to Intermediate Server Success");
			return true;
		} else {
			logger.debug("Upload to Intermediate Server Failed");
			return false;
		}
	}

	private static String signatureWithPrivateKey(EProofData config, String jsonStr) throws Exception {
		String strPk = new String(Files.readAllBytes(new File(eProofPrivateKey).toPath()));

		// handle some private key not start with -----BEGIN PRIVATE KEY-----
		int startIndex = strPk.indexOf("-----BEGIN PRIVATE KEY-----");
		strPk = strPk.substring(startIndex);

		String realPK = strPk.replaceAll("-----END PRIVATE KEY-----", "").replaceAll("-----BEGIN PRIVATE KEY-----", "")
				.replaceAll("[\r\n]", "");

		byte[] b1 = Base64.getDecoder().decode(realPK);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(b1);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(jsonStr.getBytes(StandardCharsets.UTF_8));
		byte[] sigValue = signature.sign();
		String sigValueBase64 = Base64.getEncoder().encodeToString(sigValue);

		return sigValueBase64;
	}

	private static String computeHash(String input) throws NoSuchAlgorithmException {
		byte[] vcBytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
		String vcBase64Hash = Base64.getEncoder().encodeToString(vcBytes);
		return vcBase64Hash;
	}

	public static Boolean revokeDoc(EProofData config) throws Exception {
		logger.debug("[e-Proof] Revocation - Start");
		if (!getAccessTokenByClientCredentials(config)) {
			return false;
		}

		HashMap<String, Object> requestBodyJsonMap = new HashMap<>();
		requestBodyJsonMap.put("id", config.getDocUId());
		requestBodyJsonMap.put("isRevoked", true);
		requestBodyJsonMap.put("isWithdrawn", true);

		HttpResponse response = HttpRequest.put(eProofHost + "/revocation").bearerAuth(config.getAccessToken()).body(JSONObject.toJSONString(requestBodyJsonMap))
				.execute();

		JSONObject jsonObj = JSONObject.parseObject(response.body());
		if (jsonObj.get("status") != null && jsonObj.get("status").toString().equalsIgnoreCase("Successful")) {
			logger.debug("[e-Proof] Revocation - Success");
			return true;
		} else {
			logger.debug("[e-Proof] Revocation - Failed");
			return false;
		}
	}

}