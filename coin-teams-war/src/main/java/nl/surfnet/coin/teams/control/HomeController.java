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

/**
 *
 */
package nl.surfnet.coin.teams.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.opensocial.models.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;

import nl.surfnet.coin.api.client.domain.Group20Entry;
import nl.surfnet.coin.teams.domain.GroupProvider;
import nl.surfnet.coin.teams.domain.GroupProviderUserOauth;
import nl.surfnet.coin.teams.domain.Invitation;
import nl.surfnet.coin.teams.domain.Pager;
import nl.surfnet.coin.teams.domain.Team;
import nl.surfnet.coin.teams.domain.TeamResultWrapper;
import nl.surfnet.coin.teams.interceptor.LoginInterceptor;
import nl.surfnet.coin.teams.service.GroupProviderService;
import nl.surfnet.coin.teams.service.GroupService;
import nl.surfnet.coin.teams.service.GrouperTeamService;
import nl.surfnet.coin.teams.service.TeamInviteService;
import nl.surfnet.coin.teams.util.TeamEnvironment;
import nl.surfnet.coin.teams.util.ViewUtil;

/**
 * @author steinwelberg
 *         <p/>
 *         {@link Controller} that handles the home page of a logged in user.
 */
@Controller
public class HomeController {

  @Autowired
  private MessageSource messageSource;

  @Autowired
  private LocaleResolver localeResolver;

  @Autowired
  private GrouperTeamService grouperTeamService;

  @Autowired
  private TeamInviteService teamInviteService;

  @Autowired
  private TeamEnvironment environment;

  @Autowired
  private GroupProviderService groupProviderService;

  @Autowired
  private GroupService groupService;

  private static final int PAGESIZE = 10;

  @RequestMapping("/home.shtml")
  public String start(ModelMap modelMap, HttpServletRequest request,
                      @RequestParam(required = false, defaultValue = "my") String teams,
                      @RequestParam(required = false) String teamSearch,
                      @RequestParam(required = false) Long groupProviderId) {

    Person person = (Person) request.getSession().getAttribute(
            LoginInterceptor.PERSON_SESSION_KEY);
    String display = teams;
    String query = teamSearch;
    modelMap.addAttribute("groupProviderId", groupProviderId);

    if ("externalGroups".equals(display)) {
      modelMap.addAttribute("display", display);
    } else {
      addTeams(query, person.getId(), display, modelMap, request);
    }

    String email = person.getEmail();
    if (StringUtils.hasText(email)) {
      List<Invitation> invitations = teamInviteService.findPendingInvitationsByEmail(email);
      modelMap.addAttribute("myinvitations", !CollectionUtils.isEmpty(invitations));
    }

    // Get the OAuth keys for external group providers
    final List<GroupProviderUserOauth> groupProviderUserOauths = groupProviderService.getGroupProviderUserOauths(person.getId());
    List<GroupProvider> groupProviders = new ArrayList<GroupProvider>();

    for (GroupProviderUserOauth oauth : groupProviderUserOauths) {
      // Get the external group provider belonging to this OAuth key
      final GroupProvider groupProvider = groupProviderService.getGroupProviderByStringIdentifier(oauth.getProvider());
      groupProviders.add(groupProvider);

      // Get the external groups for the requested external group provider
      getExternalGroupsForGroupProviderId(modelMap, request, groupProviderId, oauth, groupProvider);

    }
    // Add the external group providers to the ModelMap for the navigation
    modelMap.addAttribute("groupProviders", groupProviders);

    modelMap.addAttribute("appversion", environment.getVersion());
    ViewUtil.addViewToModelMap(request, modelMap);

    return "home";
  }

  private void addTeams(String query, final String person,
                        final String display, ModelMap modelMap,
                        HttpServletRequest request) {

    Locale locale = localeResolver.resolveLocale(request);

    if (messageSource.getMessage("jsp.home.SearchTeam", null, locale).equals(query)) {
      query = null;
    }
    modelMap.addAttribute("query", query);

    int offset = getOffset(request);
    modelMap.addAttribute("offset", offset);

    TeamResultWrapper resultWrapper;
    // Display all teams when the person is empty or when display equals "all"
    if ("all".equals(display) || !StringUtils.hasText(person)) {
      modelMap.addAttribute("hasMultipleSources", true);
      if (StringUtils.hasText(query)) {
        resultWrapper = grouperTeamService.findTeams(
                person, query, offset, PAGESIZE);
      } else {
        resultWrapper = grouperTeamService.findAllTeams(
                person, offset, PAGESIZE);
      }
      modelMap.addAttribute("display", "all");
      // else always display my teams
    } else {
      modelMap.addAttribute("hasMultipleSources", grouperTeamService.findStemsByMember(person).size() > 1);
      if (StringUtils.hasText(query)) {
        resultWrapper = grouperTeamService.findTeamsByMember(
                person, query, offset, PAGESIZE);
      } else {
        resultWrapper = grouperTeamService.findAllTeamsByMember(
                person, offset, PAGESIZE);
      }
      modelMap.addAttribute("display", "my");
    }

    List<Team> teams = resultWrapper.getTeams();

    modelMap.addAttribute("pagesize", PAGESIZE);
    modelMap.addAttribute("pager", resultWrapper.getPager());
    modelMap.addAttribute("resultset", resultWrapper.getTotalCount());
    modelMap.addAttribute("teams", teams);
  }

  private void getExternalGroupsForGroupProviderId(ModelMap modelMap, HttpServletRequest request, Long groupProviderId,
                                                   GroupProviderUserOauth oauth, GroupProvider groupProvider) {
    if (groupProviderId != null && groupProviderId.equals(groupProvider.getId())) {
      int offset = getOffset(request);
      modelMap.addAttribute("offset", offset);
      final Group20Entry group20Entry = groupService.getGroup20Entry(oauth, groupProvider, PAGESIZE, offset);
      modelMap.addAttribute("group20Entry", group20Entry);
      if (group20Entry != null && PAGESIZE > offset && group20Entry.getEntry().size() <= PAGESIZE) {
        Pager pager = new Pager(group20Entry.getTotalResults(), offset, PAGESIZE);
        modelMap.addAttribute("pager", pager);
      }
    }
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

  void setTeamEnvironment(TeamEnvironment teamEnvironment) {
    this.environment = teamEnvironment;
  }

}
