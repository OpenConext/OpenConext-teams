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

import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static teams.util.ViewUtil.escapeViewParameters;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.mail.Address;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.view.RedirectView;

import teams.Application;
import teams.domain.ExternalGroup;
import teams.domain.ExternalGroupProvider;
import teams.domain.Invitation;
import teams.domain.JoinTeamRequest;
import teams.domain.Member;
import teams.domain.Pager;
import teams.domain.Person;
import teams.domain.Role;
import teams.domain.Team;
import teams.domain.TeamExternalGroup;
import teams.interceptor.LoginInterceptor;
import teams.service.GrouperTeamService;
import teams.service.JoinTeamRequestService;
import teams.service.TeamExternalGroupDao;
import teams.service.TeamInviteService;
import teams.service.VootClient;
import teams.util.AuditLog;
import teams.util.ControllerUtil;
import teams.util.TokenUtil;
import teams.util.ViewUtil;

/**
 * {@link Controller} that handles the detail team page of a logged in user.
 */
@Controller
@SessionAttributes({TokenUtil.TOKENCHECK})
public class DetailTeamController {

  public static final Address[] EMPTY_ADDRESSES = new Address[0];

  private static final String ADMIN = "0";
  private static final String ADMIN_LEAVE_TEAM = "error.AdminCannotLeaveTeam";
  private static final String NOT_AUTHORIZED_DELETE_MEMBER = "error.NotAuthorizedToDeleteMember";
  private static final String MEMBER_PARAM = "member";
  private static final String ROLE_PARAM = "role";

  private static final int PAGESIZE = 10;

  @Autowired
  private VootClient vootClient;

  @Autowired
  private GrouperTeamService grouperTeamService;

  @Autowired
  private TeamInviteService teamInviteService;

  @Autowired
  private JoinTeamRequestService joinTeamRequestService;

  @Autowired
  private TeamExternalGroupDao teamExternalGroupDao;

  @Autowired
  private LocaleResolver localeResolver;

  @Autowired
  private ControllerUtil controllerUtil;

  @Value("${grouperPowerUser}")
  private String grouperPowerUser;

  @Value("${maxInvitations}")
  private Integer maxInvitations;

  @Autowired
  private Environment environment;

  @RequestMapping("/detailteam.shtml")
  public String start(ModelMap modelMap, HttpServletRequest request, @RequestParam("team") String teamId) throws IOException {
    if (!StringUtils.hasText(teamId)) {
      throw new IllegalArgumentException("Missing parameter for team");
    }

    Person person = (Person) request.getSession().getAttribute(LoginInterceptor.PERSON_SESSION_KEY);

    Team team = grouperTeamService.findTeamById(teamId);

    Set<Role> roles = team.getMembers().stream()
        .filter(m -> m.getId().equals(person.getId()))
        .findFirst()
        .map(Member::getRoles)
        .orElse(Collections.emptySet());

    String message = request.getParameter("mes");
    if (StringUtils.hasText(message)) {
      modelMap.addAttribute("message", message);
    }

    // Check if there is only one admin for a team
    boolean onlyAdmin = grouperTeamService.findAdmins(team).size() <= 1;
    modelMap.addAttribute("onlyAdmin", onlyAdmin);

    modelMap.addAttribute("invitations", teamInviteService.findInvitationsForTeamExcludeAccepted(team));

    int offset = getOffset(request);
    Pager membersPager = new Pager(team.getMembers().size(), offset, PAGESIZE);
    modelMap.addAttribute("pager", membersPager);

    modelMap.addAttribute("team", team);
    modelMap.addAttribute("adminRole", Role.Admin);
    modelMap.addAttribute("managerRole", Role.Manager);
    modelMap.addAttribute("memberRole", Role.Member);
    modelMap.addAttribute("noRole", Role.None);
    modelMap.addAttribute(TokenUtil.TOKENCHECK, TokenUtil.generateSessionToken());

    modelMap.addAttribute("maxInvitations", maxInvitations);

    ViewUtil.addViewToModelMap(request, modelMap);

    if (roles.contains(Role.Admin)) {
      modelMap.addAttribute("pendingRequests", getRequesters(team));
      modelMap.addAttribute(ROLE_PARAM, Role.Admin);
    } else if (roles.contains(Role.Manager)) {
      modelMap.addAttribute("pendingRequests", getRequesters(team));
      modelMap.addAttribute(ROLE_PARAM, Role.Manager);
    } else if (roles.contains(Role.Member)) {
      modelMap.addAttribute(ROLE_PARAM, Role.Member);
    } else {
      modelMap.addAttribute(ROLE_PARAM, Role.None);
    }
    if (!Role.None.equals(modelMap.get(ROLE_PARAM))) {
      addLinkedExternalGroupsToModelMap(person.getId(), teamId, modelMap);
    }
    modelMap.addAttribute("groupzyEnabled", environment.acceptsProfiles(Application.GROUPZY_PROFILE_NAME));

    return "detailteam";
  }

  private int getOffset(HttpServletRequest request) {
    int offset = 0;
    String offsetParam = request.getParameter("offset");
    if (StringUtils.hasText(offsetParam)) {
      try {
        offset = Integer.parseInt(offsetParam);
      } catch (NumberFormatException e) {
        // do nothing
      }
    }
    return offset;
  }

  private List<Person> getRequesters(Team team) {
    List<JoinTeamRequest> pendingRequests = joinTeamRequestService.findPendingRequests(team.getId());
    List<Person> requestingPersons = new ArrayList<Person>(pendingRequests.size());
    for (JoinTeamRequest joinTeamRequest : pendingRequests) {
      requestingPersons.add(new Person(joinTeamRequest.getPersonId(), null, joinTeamRequest.getEmail(), null, null, joinTeamRequest.getDisplayName()));
    }
    return requestingPersons;
  }

  private void addLinkedExternalGroupsToModelMap(String personId, String teamId, ModelMap modelMap) {
    final List<TeamExternalGroup> teamExternalGroups = teamExternalGroupDao.getByTeamIdentifier(teamId);
    if (!teamExternalGroups.isEmpty()) {
      List<ExternalGroup> groups = vootClient.groups(personId);
      Map<String, ExternalGroupProvider> groupProviderMap = new HashMap<String, ExternalGroupProvider>();
      for (ExternalGroup group : groups) {
        groupProviderMap.put(group.getGroupProviderIdentifier(), group.getGroupProvider());
      }
      modelMap.addAttribute("groupProviderMap", groupProviderMap);
      modelMap.addAttribute("teamExternalGroups", teamExternalGroups);
    }
  }

  @RequestMapping(value = "/doleaveteam.shtml", method = RequestMethod.POST)
  public RedirectView leaveTeam(ModelMap modelMap, HttpServletRequest request,
                                @ModelAttribute(TokenUtil.TOKENCHECK) String sessionToken,
                                @RequestParam() String token, @RequestParam("team") String teamId, SessionStatus status) {
    Person person = (Person) request.getSession().getAttribute(LoginInterceptor.PERSON_SESSION_KEY);
    String personId = person.getId();
    Team team = null;

    if (StringUtils.hasText(teamId)) {
      team = grouperTeamService.findTeamById(teamId);
    }

    if (team == null) {
      status.setComplete();
      modelMap.clear();
      throw new RuntimeException("Parameter error.");
    }

    Set<Member> admins = grouperTeamService.findAdmins(team);
    Member[] adminsArray = admins.toArray(new Member[admins.size()]);

    if (admins.size() == 1 && adminsArray[0].getId().equals(personId)) {
      status.setComplete();
      return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&view=%s&mes=%s", teamId, ViewUtil.getView(request), ADMIN_LEAVE_TEAM), false, true, false);
    }

    // Leave the team
    grouperTeamService.deleteMember(teamId, personId);
    AuditLog.log("User {} left team {}", personId, teamId);

    status.setComplete();
    modelMap.clear();
    return new RedirectView("home.shtml?teams=my&view=" + ViewUtil.getView(request));
  }

  @RequestMapping(value = "/dodeleteteam.shtml", method = RequestMethod.POST)
  public RedirectView deleteTeam(ModelMap modelMap, HttpServletRequest request,
                                 @ModelAttribute(TokenUtil.TOKENCHECK) String sessionToken,
                                 @RequestParam() String token, @RequestParam("team") String teamId, SessionStatus status)
    throws UnsupportedEncodingException {
    TokenUtil.checkTokens(sessionToken, token, status);

    Person person = (Person) request.getSession().getAttribute(LoginInterceptor.PERSON_SESSION_KEY);
    String personId = person.getId();

    if (!StringUtils.hasText(teamId)) {
      status.setComplete();
      modelMap.clear();
      throw new RuntimeException("Parameter error.");
    }

    Member member = grouperTeamService.findMember(teamId, personId);
    if (member.getRoles().contains(Role.Admin)) {
      // Delete the team
      Team team = grouperTeamService.findTeamById(teamId);
      final List<Invitation> invitationsForTeam = teamInviteService.findAllInvitationsForTeam(team);
      for (Invitation invitation : invitationsForTeam) {
        teamInviteService.delete(invitation);
      }
      final List<TeamExternalGroup> teamExternalGroups = teamExternalGroupDao.getByTeamIdentifier(teamId);
      for (TeamExternalGroup teamExternalGroup : teamExternalGroups) {
        teamExternalGroupDao.delete(teamExternalGroup);
      }
      grouperTeamService.deleteTeam(teamId);
      AuditLog.log("User {} deleted team {}", personId, teamId);

      status.setComplete();

      return new RedirectView("home.shtml?teams=my&view=" + ViewUtil.getView(request), false, true, false);
    }

    status.setComplete();
    modelMap.clear();
    return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&view=%s", teamId, ViewUtil.getView(request)));
  }

  @RequestMapping(value = "/dodeletemember.shtml", method = RequestMethod.GET)
  public RedirectView deleteMember(ModelMap modelMap,
                                   HttpServletRequest request,
                                   @ModelAttribute(TokenUtil.TOKENCHECK) String sessionToken,
                                   @RequestParam() String token, @RequestParam("team") String teamId, SessionStatus status) throws UnsupportedEncodingException {
    TokenUtil.checkTokens(sessionToken, token, status);
    String personId = decode(request.getParameter(MEMBER_PARAM), UTF_8.name());
    Person ownerPerson = (Person) request.getSession().getAttribute(LoginInterceptor.PERSON_SESSION_KEY);
    String ownerId = ownerPerson.getId();

    if (!StringUtils.hasText(teamId) || !StringUtils.hasText(personId)) {
      status.setComplete();
      modelMap.clear();
      throw new RuntimeException("Parameter error.");
    }

    // fetch the logged in member
    Member owner = grouperTeamService.findMember(teamId, ownerId);
    Member member = grouperTeamService.findMember(teamId, personId);

    // Check whether the owner is admin and thus is granted to delete the
    // member.
    // Check whether the member that should be deleted is the logged in user.
    // This should not be possible, a logged in user should click the resign
    // from team button.
    if (owner.getRoles().contains(Role.Admin) && !personId.equals(ownerId)) {

      // Delete the member
      grouperTeamService.deleteMember(teamId, personId);
      AuditLog.log("Admin user {} deleted user {} from team {}", ownerId, personId, teamId);

      status.setComplete();
      modelMap.clear();
      return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&view=%s", teamId, ViewUtil.getView(request)));

      // if the owner is manager and the member is not an admin he can delete the member
    } else if (owner.getRoles().contains(Role.Manager) && !member.getRoles().contains(Role.Admin) && !personId.equals(ownerId)) {
      // Delete the member
      grouperTeamService.deleteMember(teamId, personId);
      AuditLog.log("Manager user {} deleted user {} from team {}", ownerId, personId, teamId);

      status.setComplete();
      modelMap.clear();

      return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&view=%s", teamId, ViewUtil.getView(request)));
    }

    status.setComplete();
    modelMap.clear();

    return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&mes=%s&view=%s", teamId, NOT_AUTHORIZED_DELETE_MEMBER, ViewUtil.getView(request)));
  }

  @RequestMapping(value = "/doaddremoverole.shtml", method = RequestMethod.POST)
  public RedirectView addOrRemoveRole(ModelMap modelMap,
                                      HttpServletRequest request,
                                      @ModelAttribute(TokenUtil.TOKENCHECK) String sessionToken,
                                      @RequestParam() String token, SessionStatus status) throws IOException {

    TokenUtil.checkTokens(sessionToken, token, status);
    String teamId = request.getParameter("teamId");
    String memberId = request.getParameter("memberId");
    String roleString = request.getParameter("roleId");
    int offset = getOffset(request);
    String action = request.getParameter("doAction");

    if (!StringUtils.hasText(teamId)) {
      status.setComplete();
      modelMap.clear();
      return new RedirectView("home.shtml?teams=my&view=" + ViewUtil.getView(request));
    }
    if (!StringUtils.hasText(memberId) || !StringUtils.hasText(roleString)
      || !validAction(action)) {
      status.setComplete();
      modelMap.clear();

      return new RedirectView("detailteam.shtml?team={teamId}&view={view}&mes=no.role.action&offset={offset}"
        + teamId + "&view="
        + ViewUtil.getView(request) + "&mes=no.role.action" + "&offset="
        + offset);
    }
    Person person = (Person) request.getSession().getAttribute(
      LoginInterceptor.PERSON_SESSION_KEY);

    String message;
    if (action.equalsIgnoreCase("remove")) {
      Team team = grouperTeamService.findTeamById(teamId);
      // is the team null? return error
      if (team == null) {
        status.setComplete();
        modelMap.clear();
        return new RedirectView("home.shtml?teams=my&view=" + ViewUtil.getView(request));
      }
      message = removeRole(teamId, memberId, roleString, team, person.getId());
    } else {
      message = addRole(teamId, memberId, roleString, person.getId());
    }

    status.setComplete();
    modelMap.clear();

    return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&view=%s&mes=%s&offset=%d", teamId, ViewUtil.getView(request), message, offset));
  }

  private boolean validAction(String action) {
    return StringUtils.hasText(action) && (action.equalsIgnoreCase("remove") || action.equalsIgnoreCase("add"));
  }

  private String removeRole(String teamId,
                            String memberId, String roleString, Team team, String loggedInUserId) {
    // The role admin can only be removed if there are more then one admins in a team.
    if ((roleString.equals(ADMIN) && grouperTeamService.findAdmins(team).size() == 1)) {
      return "no.role.added.admin.status";
    }
    Role role = roleString.equals(ADMIN) ? Role.Admin : Role.Manager;
    if (grouperTeamService.removeMemberRole(teamId, memberId, role, loggedInUserId)) {
      AuditLog.log("User {} removed role {} from user {} in team {}", loggedInUserId, role, memberId, teamId);
      return "role.removed";
    } else {
      return "no.role.removed";
    }
  }

  private String addRole(String teamId,
                         String memberId, String roleString, String loggedInUserId) {
    Role role = roleString.equals(ADMIN) ? Role.Admin : Role.Manager;
    Member other = grouperTeamService.findMember(teamId, memberId);
    // Guests may not become admin
    if (other.isGuest() && role == Role.Admin) {
      return "no.role.added.guest.status";
    }
    if (grouperTeamService.addMemberRole(teamId, memberId, role, loggedInUserId)) {
      AuditLog.log("User {} added role {} to user {} in team {}", loggedInUserId, role, memberId, teamId);
      return "role.added";
    } else {
      return "no.role.added";
    }
  }

  @RequestMapping(value = "/dodeleterequest.shtml", method = RequestMethod.POST)
  public RedirectView deleteRequest(HttpServletRequest request,
                                    ModelMap modelMap,
                                    @ModelAttribute(TokenUtil.TOKENCHECK) String sessionToken,
                                    @RequestParam() String token, @RequestParam("team") String teamId, SessionStatus status)
    throws UnsupportedEncodingException {
    return doHandleJoinRequest(modelMap, request, sessionToken, token, teamId, status, false);
  }

  @RequestMapping(value = "/doapproverequest.shtml", method = RequestMethod.POST)
  public RedirectView approveRequest(HttpServletRequest request,
                                     ModelMap modelMap,
                                     @ModelAttribute(TokenUtil.TOKENCHECK) String sessionToken,
                                     @RequestParam() String token, @RequestParam("team") String teamId, SessionStatus status)
                                         throws UnsupportedEncodingException {
    return doHandleJoinRequest(modelMap, request, sessionToken, token, teamId, status, true);
  }

  private RedirectView doHandleJoinRequest(ModelMap modelMap,
                                           HttpServletRequest request, String sessionToken, String token, String teamId,
                                           SessionStatus status, boolean approve) throws UnsupportedEncodingException {
    TokenUtil.checkTokens(sessionToken, token, status);
    String memberId = decode(request.getParameter(MEMBER_PARAM), UTF_8.name());

    if (!StringUtils.hasText(teamId) || !StringUtils.hasText(memberId)) {
      status.setComplete();
      modelMap.clear();
      throw new RuntimeException("Missing parameters for team or member");
    }

    Team team = grouperTeamService.findTeamById(teamId);
    if (team == null) {
      status.setComplete();
      modelMap.clear();
      throw new RuntimeException("Cannot find team with id " + teamId);
    }

    JoinTeamRequest pendingRequest = joinTeamRequestService.findPendingRequest(memberId, team.getId());

    Person loggedInPerson = (Person) request.getSession().getAttribute(LoginInterceptor.PERSON_SESSION_KEY);

    // Check if there is an invitation for this approval request
    if (pendingRequest == null) {
      status.setComplete();
      modelMap.clear();
      throw new RuntimeException("Member (" + loggedInPerson.getId()
        + ") is trying to add a member " + "(" + memberId
        + ") without a membership request");
    }

    // Check if the user has the correct privileges
    if (!controllerUtil.hasUserAdministrativePrivileges(loggedInPerson, teamId)) {
      status.setComplete();
      modelMap.clear();

      return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&mes=error.NotAuthorizedForAction&view=%s", teamId, ViewUtil.getView(request)));
    }

    Person personToAddAsMember = new Person(pendingRequest.getPersonId(), null, pendingRequest.getEmail(), null, null, pendingRequest.getDisplayName());
    if (approve) {
      grouperTeamService.addMember(teamId, personToAddAsMember);
      grouperTeamService.addMemberRole(teamId, memberId, Role.Member, grouperPowerUser);
      AuditLog.log("User {} approved join-team-request of user {} in team {}", loggedInPerson.getId(), personToAddAsMember.getId(), teamId);
    }

    // Cleanup request
    joinTeamRequestService.delete(pendingRequest);
    AuditLog.log("Deleted join-team-request for user {} in team {}", pendingRequest.getPersonId(), teamId);


    Locale locale = localeResolver.resolveLocale(request);
    if (approve) {
      controllerUtil.sendAcceptMail(personToAddAsMember, team, locale);
    } else {
      controllerUtil.sendDeclineMail(personToAddAsMember, team, locale);
    }

    status.setComplete();
    modelMap.clear();

    return new RedirectView(escapeViewParameters("detailteam.shtml?team=%s&view=%s", teamId, ViewUtil.getView(request)));
  }

}
