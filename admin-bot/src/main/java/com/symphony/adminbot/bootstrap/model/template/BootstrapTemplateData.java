package com.symphony.adminbot.bootstrap.model.template;

import com.symphony.adminbot.bootstrap.model.DeveloperBootstrapState;
import com.symphony.adminbot.util.template.TemplateData;
import com.symphony.api.adminbot.model.Developer;
import com.symphony.api.adminbot.model.DeveloperSignUpForm;

import org.apache.commons.lang.WordUtils;

/**
 * Created by nick.tarsillo on 7/4/17.
 */
public class BootstrapTemplateData extends TemplateData {
  enum ReplacementEnums {
    BOT_NAME("{BOT_NAME}"),
    BOT_ID("{BOT_ID}"),
    BOT_EMAIL("{BOT_EMAIL}"),
    APP_NAME("{APP_NAME}"),
    APP_ID("{APP_ID}"),
    COMPANY_NAME("{COMPANY_NAME}"),
    FIRST_NAME("{FIRST_NAME}"),
    EMAIL("{EMAIL}"),
    LAST_NAME("{LAST_NAME}"),
    PASSWORD("{PASSWORD}");

    private String replacement;

    ReplacementEnums(String replacement){
      this.replacement = replacement;
    }

    public String getReplacement() {
      return replacement;
    }
  }
  
  public BootstrapTemplateData(DeveloperBootstrapState developerBootstrapState){
    Developer developer = developerBootstrapState.getDeveloper();
    DeveloperSignUpForm developerSignUpForm = developerBootstrapState.getDeveloperSignUpForm();

    if(developer != null) {
      addData(ReplacementEnums.FIRST_NAME.getReplacement(), developer.getFirstName());
      addData(ReplacementEnums.LAST_NAME.getReplacement(), developer.getLastName());
      addData(ReplacementEnums.EMAIL.getReplacement(), developer.getEmail());
    }
    if(developerSignUpForm != null) {
      addData(ReplacementEnums.APP_NAME.getReplacement(), developerSignUpForm.getAppName());
      addData(ReplacementEnums.APP_ID.getReplacement(),
          getCommonName(developerSignUpForm.getAppName()));
      addData(ReplacementEnums.COMPANY_NAME.getReplacement(),
          developerSignUpForm.getAppCompanyName());
      addData(ReplacementEnums.BOT_NAME.getReplacement(), developerSignUpForm.getBotName());
    }
    if(developerSignUpForm != null) {
      addData(ReplacementEnums.BOT_ID.getReplacement(),
          developerBootstrapState.getBootstrapInfo().getBotUsername());
      addData(ReplacementEnums.BOT_EMAIL.getReplacement(), developerSignUpForm.getBotEmail());
    }
    if(developerBootstrapState != null) {
      addData(ReplacementEnums.PASSWORD.getReplacement(), developerBootstrapState.getPassword());
    }
  }

  public BootstrapTemplateData(Developer developer, DeveloperSignUpForm developerSignUpForm, String password){
    if(developerSignUpForm != null){
      addData(ReplacementEnums.APP_NAME.getReplacement(), developerSignUpForm.getAppName());
      addData(ReplacementEnums.APP_ID.getReplacement(), getCommonName(developerSignUpForm.getAppName()));
      addData(ReplacementEnums.COMPANY_NAME.getReplacement(), developerSignUpForm.getAppCompanyName());
      addData(ReplacementEnums.BOT_NAME.getReplacement(), developerSignUpForm.getBotName());
      addData(ReplacementEnums.BOT_EMAIL.getReplacement(), developerSignUpForm.getBotEmail());
    }
    if(developer != null) {
      addData(ReplacementEnums.FIRST_NAME.getReplacement(), developer.getFirstName());
      addData(ReplacementEnums.LAST_NAME.getReplacement(), developer.getLastName());
      addData(ReplacementEnums.EMAIL.getReplacement(), developer.getEmail());
    }
    addData(ReplacementEnums.PASSWORD.getReplacement(), password);
  }

  private String getCommonName(String name) {
    String commonName = WordUtils.capitalize(name);
    char firstIndex = commonName.charAt(0);
    commonName = commonName.replaceFirst("" + firstIndex, "" + Character.toLowerCase(firstIndex));
    commonName = commonName.replaceAll(" ", "");

    return commonName;
  }

}
