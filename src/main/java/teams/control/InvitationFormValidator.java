/*
 * Copyright 2011 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package teams.control;

import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validates {@link InvitationForm}
 */
public class InvitationFormValidator implements Validator {

  private static final EmailValidator EMAIL_VALIDATOR = new EmailValidator();

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(InvitationForm.class);
  }

  /**
   * Fails if both the input field for manual email address input is empty and there is no
   * csv file with content
   */
  @Override
  public void validate(Object target, Errors errors) {
    InvitationForm form = (InvitationForm) target;

    if (StringUtils.hasText(form.getEmails())) {
      String[] emails = form.getEmails().split(",");
      for (String email : emails) {
        if (!EMAIL_VALIDATOR.isValid(email.trim(), null)) {
          errors.rejectValue("emails", "error.WrongFormattedEmailList");
          break;
        }
      }
    }

    if (form.hasCsvFile() && form.getCsvFile().isEmpty()) {
      errors.rejectValue("csvFile", "invite.errors.EmptyCSV");
    }

    if (!StringUtils.hasText(form.getEmails()) && !form.hasCsvFile()) {
      errors.rejectValue("emails", "invite.errors.NoEmailAddresses");
    }

  }
}
