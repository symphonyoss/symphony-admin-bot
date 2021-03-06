/*
 * Copyright 2017 The Symphony Software Foundation
 *
 * Licensed to The Symphony Software Foundation (SSF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package com.symphony.adminbot.bootstrap.service;

import com.symphony.adminbot.bootstrap.model.DeveloperBootstrapState;
import com.symphony.adminbot.commons.BotConstants;
import com.symphony.adminbot.config.BotConfig;
import com.symphony.adminbot.util.file.FileUtil;
import com.symphony.api.adminbot.model.Developer;
import com.symphony.api.clients.AttachmentsClient;
import com.symphony.api.clients.SecurityClient;
import com.symphony.api.pod.client.ApiException;
import com.symphony.api.pod.model.CompanyCert;
import com.symphony.api.pod.model.CompanyCertAttributes;
import com.symphony.api.pod.model.CompanyCertDetail;
import com.symphony.api.pod.model.CompanyCertStatus;
import com.symphony.api.pod.model.CompanyCertType;
import com.symphony.api.pod.model.Stream;

import org.apache.commons.lang.StringUtils;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.x500.X500Principal;
import javax.ws.rs.InternalServerErrorException;

/**
 * Created by nick.tarsillo on 7/2/17.
 */
public class DeveloperCertService {
  private static final Logger LOG = LoggerFactory.getLogger(DeveloperCertService.class);

  private AttachmentsClient attachmentsClient;
  private SecurityClient securityClient;

  public DeveloperCertService(SecurityClient securityClient, AttachmentsClient attachmentsClient){
    this.securityClient = securityClient;
    this.attachmentsClient = attachmentsClient;
  }

  /**
   * Generates cert and registers it on the pod.
   * Adds company cert info to bootstrap state.
   * @param commonName name for the cert
   * @param password password for cert
   * @return details about the cert
   */
  public CompanyCertDetail generateAndRegisterCert(String commonName, String password,
      DeveloperBootstrapState bootstrapState) {
    try {
      KeyPair keys = createKeyPair("RSA", 2048);

      //Generate cert
      X509Certificate certificate = generateCertificate(
          commonName, keys, BotConstants.VALID_DURATION);
      writeCert(commonName, certificate, keys,
          System.getProperty(BotConfig.DEVELOPER_P12_DIR), password.toCharArray());

      //Register new cert
      CompanyCert companyCert = new CompanyCert();
      companyCert.setPem(convertCertificateToPEM(certificate));

      CompanyCertAttributes companyCertAttributes = new CompanyCertAttributes();
      companyCertAttributes.setName(commonName + ".cer");

      CompanyCertStatus status = new CompanyCertStatus();
      status.setType(CompanyCertStatus.TypeEnum.TRUSTED);
      companyCertAttributes.setStatus(status);

      CompanyCertType certType = new CompanyCertType();
      certType.setType(CompanyCertType.TypeEnum.USER);
      companyCertAttributes.setType(certType);
      companyCert.attributes(companyCertAttributes);

      //Save cert info for later
      CompanyCertDetail companyCertDetail = securityClient.createCert(companyCert);
      bootstrapState.getCompanyCertMap().put(commonName, companyCert);

      LOG.info("Generated and registered new cert " + commonName + ".");
      return companyCertDetail;
    } catch (NoSuchProviderException | IOException | CertificateException |
        NoSuchAlgorithmException | ApiException e) {
      LOG.error("Error occurred when creating welcome package: ", e);
      throw new InternalServerErrorException(BotConstants.INTERNAL_ERROR);
    }
  }

  /**
   * Uploads certs as a zip attachment to partner IM.
   * If developer room is null, will upload to developer IM instead.
   * @param developerState the current state of the partner in the sign up process
   */
  public void uploadCerts(DeveloperBootstrapState developerState){
    try {
      String path = System.getProperty(BotConfig.DEVELOPER_P12_DIR);
      Developer developer = developerState.getDeveloper();
      String outputPath = path + developer.getFirstName() + developer.getLastName() + "Certs.zip";
      Set<String> certPaths = new HashSet<>();

      certPaths.add(path + developerState.getBootstrapInfo().getBotUsername() + ".p12");

      if(StringUtils.isNotBlank(developerState.getBootstrapInfo().getAppId())) {
        certPaths.add(path + developerState.getBootstrapInfo().getAppId() + ".p12");
      }

      File zip = FileUtil.zipFiles(outputPath, certPaths);

      Set<File> attachments = new HashSet<>();
      attachments.add(zip);

      Stream stream = developerState.getDeveloperIM();
      if(developerState.getDeveloperRoom() != null) {
        stream = developerState.getDeveloperRoom();
      }

      developerState.setCertAttachmentInfo(
          attachmentsClient.uploadAttachments(stream, attachments));

      if(developerState.getDeveloperRoom() != null) {
        LOG.info("Uploaded certs to room create by user " + developerState.getUserDetail()
            .getUserAttributes()
            .getUserName() + ".");
      } else {
        LOG.info("Uploaded certs to IM for user " + developerState.getUserDetail()
            .getUserAttributes()
            .getUserName() + ".");
      }
    } catch (Exception e){
      LOG.error("Error occurred when uploading attachments: ", e);
      throw new InternalServerErrorException(BotConstants.INTERNAL_ERROR);
    }
  }

  /**
   * Converts cert to PEM key
   * @param signedCertificate the cert to convert
   * @return the PEM
   */
  private String convertCertificateToPEM(X509Certificate signedCertificate) throws
      IOException {
    StringWriter signedCertificatePEMDataStringWriter = new StringWriter();
    JcaPEMWriter pemWriter = new JcaPEMWriter(signedCertificatePEMDataStringWriter);
    pemWriter.writeObject(signedCertificate);
    pemWriter.close();
    return signedCertificatePEMDataStringWriter.toString();
  }

  /**
   * Generate an X509 cert for use as the keystore cert chain
   * @return the cert
   */
  private X509Certificate generateCertificate(String name, KeyPair keys, int validDuration)
      throws CertificateException {
    X509Certificate cert;

    // backdate the start date by a day
    Calendar start = Calendar.getInstance();
    start.add(Calendar.DATE, -1);
    java.util.Date startDate = start.getTime();

    // what is the end date for this cert's validity?
    Calendar end = Calendar.getInstance();
    end.add(Calendar.DATE, validDuration);
    java.util.Date endDate = end.getTime();

    try {
      X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
          new X500Principal("CN=" + name),
          BigInteger.ONE,
          startDate, endDate,
          new X500Principal("CN=" + name),
          keys.getPublic());

      AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA");
      AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
      AsymmetricKeyParameter keyParam = PrivateKeyFactory.createKey(keys.getPrivate().getEncoded());
      ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(keyParam);
      X509CertificateHolder certHolder = certBuilder.build(sigGen);

      // now lets convert this thing back to a regular old java cert
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      InputStream certIs = new ByteArrayInputStream(certHolder.getEncoded());
      cert = (X509Certificate) cf.generateCertificate(certIs);
      certIs.close();
    } catch(CertificateException ce) {
      LOG.error("CertificateException creating or validating X509 certificate for user: " + ce);
      throw new CertificateException("Cert generation failed.");
    } catch(Exception ex) {
      LOG.error("Unknown exception creating or validating X509 certificate for user : " + ex);
      throw new InternalError("Internal error.");
    }

    return cert;
  }

  /**
   * Writes cert to file
   * @param alias the alias to save the cert as
   * @param certificate the certificate to save
   * @param keys the key pair for the cert
   * @param path the path to save the cert to
   * @param password the cert password
   */
  private void writeCert(String alias, Certificate certificate, KeyPair keys, String path, char[] password){
    Certificate[] outChain = {certificate};
    try {
      KeyStore outStore = KeyStore.getInstance("PKCS12");
      outStore.load(null, password);
      outStore.setKeyEntry(alias, keys.getPrivate(), password, outChain);
      OutputStream outputStream = new FileOutputStream(path + alias + ".p12");
      outStore.store(outputStream, password);
      outputStream.flush();
      outputStream.close();
    } catch (KeyStoreException e) {
      e.printStackTrace();
    } catch (CertificateException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates a key pair
   * @param encryptionType the encryption type
   * @param byteCount the byte count
   * @return a new key pair
   */
  private KeyPair createKeyPair(String encryptionType, int byteCount)
      throws NoSuchProviderException, NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
        encryptionType, BouncyCastleProvider.PROVIDER_NAME);
    keyPairGenerator.initialize(byteCount);
    KeyPair keyPair = keyPairGenerator.genKeyPair();
    return keyPair;
  }

}
