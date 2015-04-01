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

package nl.surfnet.coin.teams.interceptor;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import nl.surfnet.coin.teams.domain.Person;

/**
 * Like the LoginInterceptor but gets the user id from the environment instead
 * of Shibboleth.
 */
public class MockLoginInterceptor extends HandlerInterceptorAdapter {

  private static final String MOCK_USER_ATTR = "mockUser";
  private static final String MOCK_MEMBER_STATUS = "member";

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //no login required for landingpage, css and js
    if (request.getRequestURI().contains("landingpage.shtml") ||
      request.getRequestURI().contains(".js") ||
      request.getRequestURI().contains(".css") ||
      request.getRequestURI().contains(".png")) {
      return true;
    }

    HttpSession session = request.getSession();
    if (null == session.getAttribute(LoginInterceptor.PERSON_SESSION_KEY) &&
      StringUtils.isBlank(request.getParameter(MOCK_USER_ATTR))) {
      sendLoginHtml(response);
      return false;
    } else if (null == session.getAttribute(LoginInterceptor.PERSON_SESSION_KEY)) {
      //handle mock user
      String userId = request.getParameter(MOCK_USER_ATTR);
      Person person = new Person(userId, userId, userId + "@mockorg.org", "mockorg.org", "member", userId);
      session.setAttribute(LoginInterceptor.PERSON_SESSION_KEY, person);

      //handle guest status
      session.setAttribute(LoginInterceptor.USER_STATUS_SESSION_KEY, MOCK_MEMBER_STATUS);
    }
    return true;
  }

  private void sendLoginHtml(HttpServletResponse response) {
    try {
      IOUtils.copy(new ClassPathResource("mockLogin.html").getInputStream(), response.getOutputStream());
    } catch (IOException e) {
      throw new RuntimeException("Unable to serve the mockLogin.html file", e);
    }
  }
}
