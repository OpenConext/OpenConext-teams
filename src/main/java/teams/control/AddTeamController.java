/*
 * Copyright 2012 SURFnet bv, The Netherlands
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import teams.Application;
import teams.domain.Invitation;
import teams.domain.InvitationMessage;
import teams.domain.Language;
import teams.domain.Person;
import teams.domain.Role;
import teams.domain.Stem;
import teams.domain.Team;
import teams.service.GrouperTeamService;
import teams.service.TeamInviteService;
import teams.util.AuditLog;
import teams.util.ControllerUtil;
import teams.util.DuplicateTeamException;
import teams.util.PermissionUtil;
import teams.util.TokenUtil;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static teams.control.AddMemberController.INVITE_SEND_INVITE_SUBJECT;
import static teams.interceptor.LoginInterceptor.PERSON_SESSION_KEY;
import static teams.util.TokenUtil.TOKENCHECK;
import static teams.util.TokenUtil.checkTokens;
import static teams.util.ViewUtil.escapeViewParameters;

/**
 * {@link Controller} that handles the add team page of a logged in user.
 */
@Controller
@SessionAttributes(TokenUtil.TOKENCHECK)
public class AddTeamController {

  @Autowired
  private GrouperTeamService grouperTeamService;

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private Environment environment;

  @Autowired
  private ControllerUtil controllerUtil;

  @Autowired
  private TeamInviteService teamInviteService;

  @Value("${defaultStemName}")
  private String defaultStemName;

  @Value("${grouperPowerUser}")
  private String grouperPowerUser;

  @InitBinder
  protected void initBinder(ServletRequestDataBinder binder) throws Exception {
    binder.registerCustomEditor(Stem.class, "stem", new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) {
        Stem stem = new Stem(text, "", "");
        setValue(stem);
      }
    });
  }

  @RequestMapping(value = "/addteam.shtml", method = GET)
  public String addTeam(Model model, Locale locale, HttpServletRequest request) {
    if (PermissionUtil.isGuest(request)) {
      return "redirect:home.shtml";
    }

    Person person = (Person) request.getSession().getAttribute(PERSON_SESSION_KEY);

    List<Stem> stems = getStemsForMember(person);

    AddTeamCommand command = new AddTeamCommand();
    command.setAdmin2Language(Language.find(locale).orElse(Language.English));

    model.addAttribute("hasMultipleStems", stems.size() > 1);
    model.addAttribute("stems", stems);
    model.addAttribute(TOKENCHECK, TokenUtil.generateSessionToken());
    model.addAttribute("addTeamCommand", command);

    return "addteam";
  }

  @RequestMapping(value = "/doaddteam.shtml", method = POST)
  public String doAddTeam(
    @RequestParam String token,
    @ModelAttribute(TOKENCHECK) String sessionToken,
    @Valid @ModelAttribute AddTeamCommand addTeamCommand,
    BindingResult bindingResult,
    Model model,
    HttpServletRequest request, SessionStatus status) throws IOException {

    Person person = (Person) request.getSession().getAttribute(PERSON_SESSION_KEY);

    checkTokens(sessionToken, token, status);
    checkNotGuest(request);
    checkAllowedStem(addTeamCommand.getStem(), person);

    if (bindingResult.hasErrors()) {
      return "addteam";
    }

    String stemId = addTeamCommand.getStem() != null ? addTeamCommand.getStem().getId() : defaultStemName;
    String teamName = addTeamCommand.getTeamName().replace(":", "");
    String teamDescription = addTeamCommand.getTeamDescription();

    String teamId;
    try {
      teamId = grouperTeamService.addTeam(teamName, teamName, teamDescription, stemId);

      AuditLog.log("User {} added team (name: {}, id: {}) with stem {}", person.getId(), teamName, teamId, stemId);
    } catch (DuplicateTeamException e) {
      bindingResult.rejectValue("teamName", "jsp.addteam.error.duplicate");
      return "addteam";
    }
    Team team = grouperTeamService.findTeamById(teamId);

    inviteAdmin(team, addTeamCommand, teamId, person);

    grouperTeamService.setVisibilityGroup(teamId, addTeamCommand.isViewable());
    grouperTeamService.addMember(team, person);
    grouperTeamService.addMemberRole(team, person.getId(), Role.Admin, grouperPowerUser);

    status.setComplete();

    if (environment.acceptsProfiles(Application.GROUPZY_PROFILE_NAME)) {
      return escapeViewParameters("redirect:/%s/service-providers.shtml", teamId);
    } else {
      return escapeViewParameters("redirect:detailteam.shtml?team=%s", teamId);
    }
  }

  @ModelAttribute("languages")
  public Language[] languages() {
    Locale locale = LocaleContextHolder.getLocale();
    if (locale.toString().equals("en")) {
      return Language.enLanguages();
    } else {
      return Language.nlLanguages();
    }
  }

  private void checkAllowedStem(Stem stem, Person person) {
    if (stem != null && !isPersonUsingAllowedStem(person, stem.getId())) {
      throw new RuntimeException("User is not allowed to add a team!");
    }
  }

  private void checkNotGuest(HttpServletRequest request) {
    if (PermissionUtil.isGuest(request)) {
      throw new RuntimeException("User is not allowed to add a team!");
    }
  }

  private void inviteAdmin(Team team, AddTeamCommand command, String teamId, Person inviter) {
    if (!StringUtils.hasText(command.getAdmin2Email())) {
      return;
    }

    Invitation invitation = new Invitation(command.getAdmin2Email(), teamId);
    invitation.setIntendedRole(Role.Admin);
    invitation.setTimestamp(new Date().getTime());
    invitation.setLanguage(command.getAdmin2Language());

    InvitationMessage message = new InvitationMessage(command.getAdmin2Message(), inviter.getId());

    invitation.addInvitationMessage(message);
    teamInviteService.saveOrUpdate(invitation);
    String subject = messageSource.getMessage(INVITE_SEND_INVITE_SUBJECT, new Object[]{command.getTeamName()}, command.getAdmin2Language().locale());

    controllerUtil.sendInvitationMail(team, invitation, subject, inviter);

    AuditLog.log("Sent invitation and saved to database: team: {}, inviter: {}, hash: {}, email: {}, role: {}",
      teamId, inviter.getId(), invitation.getInvitationHash(), command.getAdmin2Email(), invitation.getIntendedRole());
  }

  private List<Stem> getStemsForMember(Person person) {
    return grouperTeamService.findStemsByMember(person.getId()).stream()
      .filter(stem -> stem.getId().equalsIgnoreCase(defaultStemName) || controllerUtil.isPersonMemberOfTeam(person, grouperTeamService.findTeamById(stem.getId() + ":" + "members")))
      .collect(Collectors.toList());
  }

  private boolean isPersonUsingAllowedStem(Person person, String stemId) {
    return getStemsForMember(person).stream()
      .anyMatch(allowedStem -> allowedStem.getId().equalsIgnoreCase(stemId));
  }
}
