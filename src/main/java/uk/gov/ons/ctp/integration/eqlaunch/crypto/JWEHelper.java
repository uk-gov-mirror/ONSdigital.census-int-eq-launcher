package uk.gov.ons.ctp.integration.eqlaunch.crypto;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.jwk.RSAKey;
import java.text.ParseException;
import org.apache.commons.lang3.StringUtils;
import uk.gov.ons.ctp.common.error.CTPException;

/** Helper class for encrypting, decrypting a JWS as the payload of a JWE. */
public class JWEHelper {

  private static final Logger log = LoggerFactory.getLogger(JWEHelper.class);

  /**
   * Encrypt JWS as payload of JWE
   *
   * @param jws payload
   * @param key cryptographic key to use for encryption
   * @return
   * @throws CTPException
   */
  public String encrypt(JWSObject jws, Key key) throws CTPException {
    log.with(key.getKid()).debug("Encrypting with public key");
    JWEHeader jweHeader = buildHeader(key);
    Payload payload = new Payload(jws);
    JWEObject jweObject = new JWEObject(jweHeader, payload);

    RSAKey jwk = (RSAKey) key.getJWK();

    try {
      jweObject.encrypt(new RSAEncrypter(jwk));
      return jweObject.serialize();
    } catch (JOSEException e) {
      log.with("kid", key.getKid()).error("Failed to encrypt JWE");
      throw new CTPException(CTPException.Fault.SYSTEM_ERROR, "Failed to encrypt JWE");
    }
  }

  private JWEHeader buildHeader(Key key) {

    // We HAVE to use the deprecated Algo to remain compatible with EQ
    JWEHeader jweHeader =
        new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A256GCM)
            .keyID(key.getKid())
            .build();
    return jweHeader;
  }

  /**
   * Return key hint (Id) from JWE header
   *
   * @param jwe JWE encrypted String
   * @return String representing Key hint from header
   * @throws CTPException when fails to retrieve key Id from header.
   */
  public String getKid(String jwe) throws CTPException {
    try {
      JWEObject jweObject = JWEObject.parse(jwe);
      String keyId = jweObject.getHeader().getKeyID();
      if (StringUtils.isEmpty(keyId)) {
        log.with("jwe", jwe).error("Failed to extract key Id from JWE header");
        throw new CTPException(
            CTPException.Fault.SYSTEM_ERROR, "Failed to extract key Id from JWE header");
      }
      return keyId;
    } catch (ParseException e) {
      log.with("jwe", jwe).error("Failed to parse JWE string");
      throw new CTPException(CTPException.Fault.SYSTEM_ERROR, "Failed to parse JWE string");
    }
  }

  /**
   * Decrypt JWE returning JWS payload.
   *
   * @param jwe JWE encrypted String
   * @param key Cryptographic key to decrypt JWE
   * @return JWSObject representing payload
   * @throws CTPException
   */
  public JWSObject decrypt(String jwe, Key key) throws CTPException {

    JWEObject jweObject;
    try {
      jweObject = JWEObject.parse(jwe);
    } catch (ParseException e) {
      log.with("jwe", jwe).with("kid", key.getKid()).error("Failed to parse JWE string");
      throw new CTPException(CTPException.Fault.SYSTEM_ERROR, "Failed to parse JWE string");
    }

    try {
      jweObject.decrypt(new RSADecrypter((RSAKey) key.getJWK()));
    } catch (JOSEException e) {
      log.with("jwe", jwe)
          .with("kid", key.getKid())
          .error("Failed to decrypt JWE with provided key");
      throw new CTPException(
          CTPException.Fault.SYSTEM_ERROR, "Failed to decrypt JWE with provided key");
    }

    Payload payload = jweObject.getPayload();
    if (payload == null) {
      log.with("jwe", jwe).with("kid", key.getKid()).error("Extracted JWE Payload null");
      throw new CTPException(CTPException.Fault.SYSTEM_ERROR, "Extracted JWE Payload null");
    }

    return payload.toJWSObject();
  }
}
