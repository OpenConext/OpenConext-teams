<%@ taglib uri="http://www.springframework.org/tags" prefix="spring"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib tagdir="/WEB-INF/tags" prefix="teams"%>
<teams:genericpage>
<!-- = TeamContainer -->
<div class="section" id="TeamContainer">
	<!-- = Header -->
	<div id="Header">
		<p class="back not-member"><a href="home.shtml?teams=all">&lt; <spring:message code='jsp.detailteam.Back' /></a></p>
		<h1><c:out value="${team.name}" /></h1>
	<!-- / Header -->
	</div>
	<!-- = Content -->
	<div id="Content">
		<p class="description">
			<c:set var="noDescription"><spring:message code='jsp.general.NoDescription' /></c:set>
			<c:out value="${team.description}" default="${noDescription}"/>
		</p>
		<p class="more">
			<c:url value="jointeam.shtml" var="joinUrl"><c:param name="team" value="${team.id}" /></c:url>
			<a class="button-primary" href="<c:out value='${joinUrl}' />"><spring:message code='jsp.detailteam.Join' /></a>		
		</p>
		<br class="clear" />
	<!-- / Content -->
	</div>
<!-- / TeamContainer -->
</div>
</teams:genericpage>