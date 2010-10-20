/**
 * Copyright 2010
 */
package nl.surfnet.coin.teams.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.surfnet.coin.teams.domain.Member;
import nl.surfnet.coin.teams.domain.Role;
import nl.surfnet.coin.teams.domain.Team;
import nl.surfnet.coin.teams.interceptor.LoginInterceptor;
import nl.surfnet.coin.teams.service.TeamService;
import nl.surfnet.coin.teams.util.TeamEnvironment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import edu.internet2.middleware.grouperClient.api.GcAddMember;
import edu.internet2.middleware.grouperClient.api.GcFindGroups;
import edu.internet2.middleware.grouperClient.api.GcGetGrouperPrivilegesLite;
import edu.internet2.middleware.grouperClient.api.GcGetGroups;
import edu.internet2.middleware.grouperClient.api.GcGetMembers;
import edu.internet2.middleware.grouperClient.api.GcGroupSave;
import edu.internet2.middleware.grouperClient.ws.beans.WsAddMemberResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsFindGroupsResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsGetGroupsResult;
import edu.internet2.middleware.grouperClient.ws.beans.WsGetMembersResult;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroup;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroupSaveResults;
import edu.internet2.middleware.grouperClient.ws.beans.WsGroupToSave;
import edu.internet2.middleware.grouperClient.ws.beans.WsGrouperPrivilegeResult;
import edu.internet2.middleware.grouperClient.ws.beans.WsQueryFilter;
import edu.internet2.middleware.grouperClient.ws.beans.WsSubject;
import edu.internet2.middleware.grouperClient.ws.beans.WsSubjectLookup;

/**
 * {@link TeamService} using Grouper LDAP as persistent store
 * 
 */
// @Component("teamService")
public class GrouperTeamService implements TeamService {

  @Autowired
  private TeamEnvironment environment;

  private static String[] FORBIDDEN_CHARS = new String[] { "<", ">", "/", "\\",
      "*", ":" };

  /*
   * (non-Javadoc)
   * 
   * @see nl.surfnet.coin.teams.service.TeamService#findAllTeams()
   */
  @Override
  public List<Team> findAllTeams() {
    GcFindGroups findGroups = new GcFindGroups();
    findGroups.assignActAsSubject(getActAsSubject());
    findGroups.assignIncludeGroupDetail(Boolean.TRUE);

    WsQueryFilter queryFilter = new WsQueryFilter();
    queryFilter.setQueryFilterType("FIND_BY_STEM_NAME");
    queryFilter.setStemName(environment.getDefaultStemName());
    findGroups.assignQueryFilter(queryFilter);
    WsFindGroupsResults findResults = findGroups.execute();
    WsGroup[] groupResults = findResults.getGroupResults();
    return convertWsGroupToTeam(groupResults);

  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * nl.surfnet.coin.teams.service.TeamService#findTeamById(java.lang.String)
   */
  @Override
  public Team findTeamById(String teamId) {
    GcFindGroups findGroups = new GcFindGroups();
    WsSubjectLookup actAsSubject = getActAsSubject();
    findGroups.assignActAsSubject(actAsSubject);
    findGroups.assignIncludeGroupDetail(Boolean.TRUE);
    findGroups.addGroupName(teamId);
    WsFindGroupsResults findResults = findGroups.execute();
    WsGroup[] groupResults = findResults.getGroupResults();
    if (groupResults.length == 0) {
      throw new RuntimeException("No team found with Id('" + teamId + "')");
    }
    WsGroup wsGroup = groupResults[0];
    return new Team(wsGroup.getName(), wsGroup.getDisplayExtension(),
        wsGroup.getDescription(), getMembers(wsGroup.getName()), Boolean.TRUE);
  }

  /*
   * 
   */
  private WsSubjectLookup getActAsSubject() {
    return getActAsSubject(false);
  }

  /*
   * 
   */
  private WsSubjectLookup getActAsSubject(boolean powerUser) {
    WsSubjectLookup actAsSubject = new WsSubjectLookup();
    if (powerUser) {
      actAsSubject.setSubjectId(environment.getGrouperPowerUser());
    } else {
      actAsSubject.setSubjectId(LoginInterceptor.getLoggedInUser());
    }
    return actAsSubject;
  }

  /*
   * (non-Javadoc)
   * 
   * @see nl.surfnet.coin.teams.service.TeamService#findTeams(java.lang.String)
   */
  @Override
  public List<Team> findTeams(String partOfTeamName) {
    GcFindGroups findGroups = new GcFindGroups();
    findGroups.assignActAsSubject(getActAsSubject());
    findGroups.assignIncludeGroupDetail(Boolean.TRUE);

    WsQueryFilter queryFilter = new WsQueryFilter();
    queryFilter.setQueryFilterType("FIND_BY_GROUP_NAME_APPROXIMATE");
    queryFilter.setGroupName(partOfTeamName);
    findGroups.assignQueryFilter(queryFilter);
    WsFindGroupsResults findResults = findGroups.execute();
    WsGroup[] groupResults = findResults.getGroupResults();
    return convertWsGroupToTeam(groupResults);
  }

  private List<Team> convertWsGroupToTeam(WsGroup[] groupResults) {
    List<Team> result = new ArrayList<Team>();
    for (WsGroup wsGroup : groupResults) {
      Team team = new Team(wsGroup.getName(), wsGroup.getDisplayExtension(),
          wsGroup.getDescription(), getMembers(wsGroup.getName()), true);
      result.add(team);
    }
    return result;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * nl.surfnet.coin.teams.service.TeamService#getTeamsByPerson(java.lang.String
   * )
   */
  @Override
  public List<Team> getTeamsByMember(String memberId) {
    GcGetGroups getGroups = new GcGetGroups();
    getGroups.addSubjectId(memberId);
    getGroups.assignActAsSubject(getActAsSubject());
    WsGetGroupsResult[] groups = getGroups.execute().getResults();
    if (groups.length > 0) {
      WsGroup[] wsGroups = groups[0].getWsGroups();
      return convertWsGroupToTeam(wsGroups);
    }
    return new ArrayList<Team>();

  }

  private Set<Member> getMembers(String teamId) {
    GcGetMembers getMember = new GcGetMembers();
    getMember.assignActAsSubject(getActAsSubject());
    getMember.assignIncludeSubjectDetail(Boolean.TRUE);
    getMember.addGroupName(teamId);
    getMember.addSubjectAttributeName("mail");
    getMember.addSubjectAttributeName("displayName");
    WsGetMembersResult[] getMembers = getMember.execute().getResults();
    Set<Member> members = new HashSet<Member>();
    if (getMembers.length > 0) {
      WsSubject[] subjects = getMembers[0].getWsSubjects();
      for (WsSubject wsSubject : subjects) {
        String name = wsSubject.getName();
        String mail = wsSubject.getAttributeValue(0);
        String displayName = wsSubject.getAttributeValue(0);
        Member member = new Member(null, displayName, name, mail);
        members.add(member);
      }
    }
    return members;
  }

  private void addRolesToMembers(Set<Member> members, String teamId) {
    GcGetGrouperPrivilegesLite privileges = new GcGetGrouperPrivilegesLite();
    privileges.assignActAsSubject(getActAsSubject());
    privileges.assignGroupName(teamId);
    WsGrouperPrivilegeResult[] privilegeResults = privileges.execute()
        .getPrivilegeResults();
    for (Member member : members) {
      String id = member.getId();
      List<WsGrouperPrivilegeResult> memberPrivs = getPrivilegeResultsForMember(
          id, privilegeResults);
      if (!memberPrivs.isEmpty()) {
        for (WsGrouperPrivilegeResult priv : memberPrivs) {
          member.addRole(getRole(priv.getPrivilegeName()));
        }

      }
    }
  }

  /**
   * @param privilegeName
   * @return
   */
  private Role getRole(String privilegeName) {
    /*
     * De grouper rechten heten "admin" voor de group administrator, en "update"
     * voor de group manager.
     * 
     * Verder worden er standaard een aantal rechten gezet zoals "read" (voor
     * het zien wie er lid is van de group) voor leden van de groep, en "view"
     * voor het zien van de groep (ook voor leden en, als dat in de GUI is
     * aangezet, voor "everyone").
     */
    return null;
  }

  private List<WsGrouperPrivilegeResult> getPrivilegeResultsForMember(
      String id, WsGrouperPrivilegeResult[] privilegeResults) {
    List<WsGrouperPrivilegeResult> result = new ArrayList<WsGrouperPrivilegeResult>();
    for (WsGrouperPrivilegeResult privilege : privilegeResults) {
      if (privilege.getOwnerSubject().getName().equals(id)) {
        result.add(privilege);
      }
    }
    return result;
  }

  /*
   * (non-Javadoc)
   * 
   * @see nl.surfnet.coin.teams.service.TeamService#addMember(java.lang.String,
   * java.lang.String)
   */
  @Override
  public void addMember(String teamId, String personId) {
    GcAddMember addMember = new GcAddMember();
    addMember.assignActAsSubject(getActAsSubject());
    addMember.assignGroupName(teamId);
    addMember.addSubjectId(personId);
    WsAddMemberResults execute = addMember.execute();
  }

  /*
   * (non-Javadoc)
   * 
   * @see nl.surfnet.coin.teams.service.TeamService#addTeam(java.lang.String,
   * java.lang.String, java.lang.String)
   */
  @Override
  public String addTeam(String teamId, String displayName,
      String teamDescription, boolean viewable) {
    if (!StringUtils.hasText(teamId)) {
      throw new IllegalArgumentException("teamId is not optional");
    }
    for (String ch : FORBIDDEN_CHARS) {
      teamId = teamId.replace(ch, "");
    }
    teamId = teamId.replace(" ", "_").toLowerCase();
    teamId = environment.getDefaultStemName() + ":" + teamId;
    GcGroupSave groupSave = new GcGroupSave();
    groupSave.assignActAsSubject(getActAsSubject());
    WsGroupToSave group = new WsGroupToSave();
    group.setSaveMode("INSERT");
    WsGroup wsGroup = new WsGroup();
    wsGroup.setDescription(teamDescription);
    wsGroup.setDisplayName(displayName);
    wsGroup.setName(teamId);
    group.setWsGroup(wsGroup);
    groupSave.addGroupToSave(group);
    groupSave.execute();
    return teamId;

  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * nl.surfnet.coin.teams.service.TeamService#deleteMember(java.lang.String,
   * java.lang.String)
   */
  @Override
  public void deleteMember(String teamId, String personId) {
    // TODO Auto-generated method stub

  }

  /*
   * (non-Javadoc)
   * 
   * @see nl.surfnet.coin.teams.service.TeamService#deleteTeam(java.lang.String)
   */
  @Override
  public void deleteTeam(String teamId) {
    // TODO Auto-generated method stub

  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * nl.surfnet.coin.teams.service.TeamService#updateMember(java.lang.String,
   * java.lang.String, nl.surfnet.coin.teams.domain.Role)
   */
  @Override
  public void updateMember(String teamId, String personId, Role role) {
    // TODO Auto-generated method stub

  }

  /*
   * (non-Javadoc)
   * 
   * @see nl.surfnet.coin.teams.service.TeamService#updateTeam(java.lang.String,
   * java.lang.String, java.lang.String)
   */
  @Override
  public void updateTeam(String teamId, String displayName,
      String teamDescription) {
    // TODO Auto-generated method stub

  }

  @Override
  public List<Team> findAllTeams(boolean viewable) {
    return findAllTeams();
  }

}