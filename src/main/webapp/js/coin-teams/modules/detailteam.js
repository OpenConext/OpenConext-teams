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

COIN.MODULES.Detailteam = function(sandbox) {
  // Public interface
  var module = {
    init: function() {

      // Add odd / even classes to table rows
      sandbox.fixTableLayout($('table.team-table'));

      // Leave team admin message box
      if ($("#__notifyBar").length > 0) {
        $.notifyBar({ close: true, cls: "error", html: "<p>" + $('#__notifyBar').html() + "</p>", delay: 100000 });
      }

      // Leave Team Confirm (appears when a user clicks
      // the "Leave" button of a team in the detailteam screen)
      $('#LeaveTeamDialog').dialog({
        autoOpen   : false,
        width      : 250,
        resizable  : false,
        modal      : true,
        dialogClass: "ui-popup",
        buttons: {
          "<spring:message code='jsp.dialog.leaveteam.Submit' />": library.leaveTeam,
          "<spring:message code='jsp.general.Cancel' />": library.cancelLeave
        },
        open: function() {
        },
        closeOnEscape: true
      });

      // Delete Team Confirm (appears when a user clicks
      // the "Delete" button of a team in the detailteam screen)
      $('#DeleteTeamDialog').dialog({
        autoOpen   : false,
        width      : 250,
        resizable  : false,
        modal      : true,
        dialogClass: "ui-popup",
        buttons: {
          "<spring:message code='jsp.dialog.deleteteam.Submit' />": library.deleteTeam,
          "<spring:message code='jsp.general.Cancel' />": library.cancelDelete
        },
        open: function() {
        },
        closeOnEscape: true
      });

      // Clicked [ Leave ]
      $(document).on("click", 'a#LeaveTeam', function(e) {
        e.preventDefault();
        $('#LeaveTeamDialog').removeClass('hide').dialog('open');
      });

      // Clicked [ Delete Team ]
      $(document).on("click", 'a#DeleteTeam', function(e) {
        e.preventDefault();
        $('#DeleteTeamDialog').removeClass('hide').dialog('open');
      });

      // Clicked [ Delete Member ]
      $(document).on("click", 'a.DeleteMember', function(e) {
        e.preventDefault();
          if (window.confirm("<spring:message code='jsp.dialog.deletemember.Confirmation' />")) {
              sandbox.redirectBrowserTo($(this).attr('href'));
          }
      });

      // Clicked [ Delete Member ]
      $(document).on("click", 'a.RemoveExternalGroup', function(e) {
        e.preventDefault();
          if (window.confirm("<spring:message code='jsp.dialog.removeexternalgroup.Confirmation' />")) {
              sandbox.redirectBrowserTo($(this).attr('href'));
          }
      });

      // Clicked [ Permission ]
      $(document).on("click", 'input[type=checkbox][name$=Role]', function() {
        library.actionRole($(this));
      });

      $(document).on("click", 'a.open_invitationinfo', function(e) {
        e.preventDefault();
        var invitationInfo = $(this).attr('id');
        $('.' + invitationInfo).toggleClass('hide');
      });

      // Clicked [ Cancel ]
      $(document).on("click", 'input[name=cancelAddMember], .close a', function(e) {
        e.preventDefault();
        var teamId = $('input[name=teamId]').val();
        if (teamId) {
          sandbox.redirectBrowserTo('detailteam.shtml?team=' + encodeURIComponent(teamId));
        } else {
          sandbox.redirectBrowserTo('home.shtml?teams=my');
        }
      });

      // Clicked [ Decline ]
      $(document).on("click", 'a.deny-join-request', function(e) {
        e.preventDefault();
        $(this).parent().find('form[name=deleteRequestForm]').submit();
      });

      // Clicked [ Add member ]
      $(document).on("click", 'a.approve-join-request', function(e) {
        e.preventDefault();
        $(this).parent().find('form[name=approveRequestForm]').submit();
      });
    },

    destroy: function() {

    }
  };

  // Private library (through closure)
  var library = {
    getMemberId: function(el) {
      if (el instanceof jQuery) {
        var idSplit = el.attr('id').split('_', '2');
        return idSplit[1];
      }
    },
    getRole: function(el) {
      if (el instanceof jQuery) {
        var idSplit = el.attr('id').split('_', '2');
        return idSplit[0];
      }
    },
    leaveTeam: function() {
      $('form#LeaveTeamForm').submit();
//      sandbox.redirectBrowserTo($('a#LeaveTeam').attr('href'));
    },
    cancelLeave: function() {
      $('#LeaveTeamDialog').addClass('hide').dialog('close');
    },
    deleteTeam: function() {
      $('form#DeleteTeamForm').submit();
//      sandbox.redirectBrowserTo($('a#DeleteTeam').attr('href'));
    },
    cancelDelete: function() {
      $('#DeleteTeamDialog').addClass('hide').dialog('close');
    },
    actionRole: function(el) {
      $('#memberId').val(library.getMemberId(el));
      $('#roleId').val(library.getRole(el));
      if (el.prop('checked')) {
        $('#doAction').val('add');
      } else {
        $('#doAction').val('remove');
      }
      $('#detailForm').submit();
    }
  };

  // Return the public interface
  return module;
};
