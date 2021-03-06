<%@ taglib uri="http://www.springframework.org/tags" prefix="spring"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib tagdir="/WEB-INF/tags" prefix="teams"%>
<%--
  Copyright 2012 SURFnet bv, The Netherlands

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  --%>
<teams:genericpage>
<%-- = Header --%>
<div id="Header">
  <ul class="team-actions">
    <c:url value="/home.shtml" var="myTeamsUrl"><c:param name="teams" value="my" /></c:url>
    <c:url value="/home.shtml" var="allTeamsUrl"><c:param name="teams" value="all" /></c:url>
    <c:url value="/myinvitations.shtml" var="myInvitationsUrl"></c:url>
    <li class="first"><a class="btn-my-teams<c:if test='${display eq "my"}'> selected</c:if>" href="<c:out value="${myTeamsUrl}"/>"><spring:message code='jsp.home.MyTeams' /></a></li>
    <c:if test="${displayExternalTeams ne false}">
      <c:forEach items="${groupProviders}" var="groupProvider">
        <spring:url value="/home.shtml" var="groupProviderUrl">
          <spring:param name="groupProviderId" value="${groupProvider.identifier}"/>
          <spring:param name="teams" value="externalGroups"/>
        </spring:url>
        <li class="middle"><a href="<c:out value="${groupProviderUrl}"/>" class="btn-all-teams <c:if test="${groupProviderId eq groupProvider.identifier}"> selected</c:if>"><c:out value="${groupProvider.name}"/></a></li>
      </c:forEach>
    </c:if>
    <c:choose>
      <c:when test="${myinvitations eq true}">
        <li class="middle"><a class="btn-all-teams<c:if test='${display eq "all"}'> selected</c:if>" href="<c:out value="${allTeamsUrl}"/>"><spring:message code='jsp.home.AllTeams' /></a></li>
        <li class="last"><a href="${myInvitationsUrl}"><spring:message code="jsp.home.MyInvitations"/></a></li>
      </c:when>
      <c:otherwise>
        <li class="last"><a class="btn-all-teams<c:if test='${display eq "all"}'> selected</c:if>" href="<c:out value="${allTeamsUrl}"/>"><spring:message code='jsp.home.AllTeams' /></a></li>
      </c:otherwise>
    </c:choose>
  </ul>



  <c:if test="${display ne 'externalGroups'}">
  <c:url value="/home.shtml" var="searchUrl"><c:param name="teams" value="${display}" /></c:url>
    <form action="<c:out value='${searchUrl}' />" method="post" id="searchTeamsForm">
      <fieldset class="search-fieldset team-search">
        <c:choose>
          <c:when test="${fn:length(query) == 0}">
            <input class="text search-query" type="text" name="teamSearch" placeholder="<spring:message code='jsp.home.SearchTeam' />"  />
          </c:when>
          <c:otherwise>
            <c:url value="/home.shtml" var="viewAllUrl"><c:param name="teams" value="${display}" /></c:url>
            <span class="view-all"><a href="${viewAllUrl}"><spring:message code='jsp.home.ViewAll' /></a></span>
            <input class="text search-query" type="text" name="teamSearch" placeholder="<spring:message code='jsp.home.SearchTeam' />"value="<c:out value='${query}' />" />
          </c:otherwise>
        </c:choose>
        <input class="submit-search" id="SubmitTeamSearch" type="submit" value="" />
      </fieldset>
    </form>
  </c:if>
  <br class="clear" />
<%-- / Header --%>
</div>
<%-- = Content --%>
<div id="Content"<c:if test="${display eq 'externalGroups'}"> class="home-external-groups"</c:if>>
  <c:if test='${sessionScope.userStatus ne "guest" and display ne "externalGroups"}'>
    <c:url value="/addteam.shtml" var="addTeamUrl"></c:url>
    <p class="add"><a class="button" href="${addTeamUrl}"><spring:message code='jsp.home.AddTeam' /></a></p>
  </c:if>
  <%--
    LOG.debug("Elapsed before paginated list: {} ms", System.currentTimeMillis() - start);
  --%>
  <teams:paginate baseUrl="home.shtml" pager="${pager}"/>
  <div class="team-table-wrapper">
    <table class="team-table">
      <thead>
        <tr>
          <th><spring:message code='jsp.home.table.Team' /></th>
          <c:choose>
            <c:when test="${display eq 'my'}">
              <th><spring:message code='jsp.home.table.Role' /></th>
              <th><spring:message code='jsp.home.table.Members' /></th>
            </c:when>
            <c:otherwise>
              <th><spring:message code='jsp.home.table.Description' /></th>
            </c:otherwise>
          </c:choose>
        </tr>
      </thead>
      <tbody>
      <c:choose>
        <c:when test="${fn:length(teams) > 0 }">
          <c:forEach items="${teams}" var="team">
            <tr>
              <c:url value="/detailteam.shtml" var="detailUrl"><c:param name="team" value="${team.id}" /></c:url>
              <c:choose>
                <c:when test="${display eq 'my'}">
                  <td><a href="<c:out value="${detailUrl}"/>"><c:out value="${team.name}" /></a>
                    <c:if test="${not empty team.description}">
                    <span class="fieldinfo">
                    <a href="#" title="<c:out value="${team.description}"/>"></a>
                    </span>
                    </c:if>
                    </td>
                  <td><c:out value="${team.viewerRole}" /></td>
                  <td><c:out value="${team.numberOfMembers}" /></td>
                </c:when>
                <c:otherwise>
                  <td><a href="${detailUrl}"><c:out value="${team.name}" /></a></td>
                  <td><c:out value="${team.descriptionAsHtml}" escapeXml="false" /></td>
                </c:otherwise>
              </c:choose>
            </tr>
          </c:forEach>
        </c:when>
        <c:when test="${not empty externalGroups and fn:length(externalGroups) > 0}">
          <c:forEach items="${externalGroups}" var="externalGroup">
            <spring:url value="/externalgroups/groupdetail.shtml" var="detailUrl">
              <spring:param name="groupId" value="${externalGroup.identifier}"/>
              <spring:param name="externalGroupProviderIdentifier" value="${externalGroup.groupProviderIdentifier}"/>
            </spring:url>
            <tr>
              <td>
                <c:out value="${externalGroup.name}"/>
              </td>
              <td><c:out value="${externalGroup.description}"/></td>
            </tr>
          </c:forEach>
        </c:when>
        <c:when test="${display eq 'externalGroups' and (empty externalGroups or fn:length(externalGroups) == 0)}">
          <tr><td colspan="4"><spring:message code="jsp.home.NoExternalGroups" /></td></tr>
        </c:when>
        <c:when test="${fn:length(query) > 0 && fn:length(teams) == 0}">
          <tr><td colspan="4"><spring:message code="jsp.home.NoTeamsFound" /></td></tr>
        </c:when>
        <c:when test="${fn:length(query) == 0 && fn:length(teams) == 0}">
          <tr><td colspan="4"><spring:message code="jsp.home.NoSearchQuery" /></td></tr>
        </c:when>
        <c:when test="${sessionScope.userStatus eq 'guest'}">
          <tr><td colspan="4"><spring:message code="jsp.home.NoTeams.Guest" /></td></tr>
        </c:when>
        <c:otherwise>
          <tr><td colspan="4"><spring:message code="jsp.home.NoTeams" /></td></tr>
        </c:otherwise>
      </c:choose>
      </tbody>
    </table>
    <table class="team-search-result">
    <thead>
        <tr>
          <th><spring:message code='jsp.home.table.Team' /></th>
          <th><spring:message code='jsp.home.table.Description' /></th>
        </tr>
    </thead>
    <tbody>
    </tbody>
    </table>
  </div>
<%-- / Content --%>
</div>

</teams:genericpage>

