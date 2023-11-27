<%@ page import="org.jetbrains.teamcity.vault.VaultConstants" %>
<%@ page import="org.jetbrains.teamcity.vault.server.VaultTestQueryController" %>
<%@ page import="jetbrains.buildServer.controllers.parameters.ParameterContext" %>
<%@include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="context" scope="request" type="jetbrains.buildServer.controllers.parameters.ParameterEditContext"/>
<jsp:useBean id="vaultFeatureSettings" scope="request"
             type="java.util.List<org.jetbrains.teamcity.vault.VaultFeatureSettings>"/>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<c:set var="project" value="<%=context.getAdditionalParameter(ParameterContext.PROJECT)%>" />
<c:set var="projectId" value='${project == null ? "" : project.externalId}' />

<bs:linkScript>
  /js/bs/testConnection.js
</bs:linkScript>


<script>
  BS.VaultParametersDialog = OO.extend(BS.AbstractWebForm, {
    formElement: () => $('parameterSpecEditForm'),

    submitTestQuery: function () {
      const that = this;
      BS.PasswordFormSaver.save(this, '<c:url value="<%=VaultTestQueryController.PATH%>"/>', OO.extend(BS.ErrorsAwareListener, {
        onFailedTestConnectionError: function (elem) {
          var text = "";
          if (elem.firstChild) {
            text = elem.firstChild.nodeValue;
          }
          BS.TestConnectionDialog.show(false, text, $('testQueryButton'));
        },

        onCompleteSave: function (form, responseXML) {
          const err = BS.XMLResponse.processErrors(responseXML, this, form.propertiesErrorsHandler);
          BS.ErrorsAwareListener.onCompleteSave(form, responseXML, err);
          if (!err) {
            this.onSuccessfulSave(responseXML);
          }
        },

        onSuccessfulSave: function (responseXML) {
          that.enable();

          let additionalInfo = "";
          const testQueryResultNodes = responseXML.documentElement.getElementsByTagName("testConnectionResult");
          if (testQueryResultNodes && testQueryResultNodes.length > 0) {
            const testQueryResult = testQueryResultNodes.item(0);
            if (testQueryResult.firstChild === "Success") {
              additionalInfo = "Variable will resolve a value";
            }
          }

          BS.TestConnectionDialog.show(true, additionalInfo, $('testQueryButton'));
        }
      }));
      return false;
    }
  });


  BS.TestConnectionDialog.afterClose = function () {
    $j('#OAuthConnectionDialog .testQueryButton').remove();
  };

  BS.TestConnectionDialog.getSuccessTitle = () => 'Test successful';

  BS.TestConnectionDialog.getFailureTitle = () => 'Test failure';
</script>

<c:set var="emptyNamespaceOption" value="<%=VaultConstants.FeatureSettings.DEFAULT_ID%>"/>
<c:set var="namespaceNotSelectedValue" value="<%=VaultConstants.ParameterSettings.NAMESPACE_NOT_SELECTED_VALUE%>"/>
<c:set var="vaultId" value="<%=VaultConstants.ParameterSettings.VAULT_ID%>"/>
<c:set var="vaultQuery" value="<%=VaultConstants.ParameterSettings.VAULT_QUERY%>"/>
<c:set var="currentId" value="${propertiesBean.properties[vaultId]}" scope="request"/>

<table class="runnerFormTable">
  <tr>
    <th style="width: 20%"><label for="prop:${vaultId}">Vault ID: <l:star/></label></th>
    <td>
      <props:selectProperty id="${vaultId}" name="${vaultId}" className="longField">
        <props:option value="${namespaceNotSelectedValue}">-- Please choose namespace --</props:option>
        <c:forEach items="${vaultFeatureSettings}" var="feature">
          <c:choose>
            <c:when test="${empty feature.id}">
              <props:option value="${emptyNamespaceOption}" selected="${empty currentId}">
                <c:out value="Default Namespace (empty)"/>
              </props:option>
            </c:when>
            <c:otherwise>
              <forms:option value="${feature.id}" selected="${currentId == feature.id}">
                <c:choose>
                  <c:when test="${empty feature.displayName}">
                    <c:out value="${feature.id}"/>
                  </c:when>
                  <c:otherwise>
                    <c:out value="${feature.displayName}"/>
                  </c:otherwise>
                </c:choose>
              </forms:option>
            </c:otherwise>
          </c:choose>
        </c:forEach>
      </props:selectProperty>
    </td>
  </tr>
  <tr>
    <th style="width: 20%"><label for=prop:"${vaultQuery}">Vault Query: <l:star/></label></th>
    <td>
      <props:textProperty name="${vaultQuery}" className="longField"/>
    </td>
  </tr>
  <props:hiddenProperty name="projectId" value="${projectId}"/>

  <tr>
    <td style="border-top: 0">
      <forms:submit id="testQueryButton" type="button" label="Test Query" className="testQueryButton" onclick="return BS.VaultParametersDialog.submitTestQuery();"/>
      <bs:dialog dialogId="testConnectionDialog" title="Test Query" closeCommand="BS.TestConnectionDialog.close();" closeAttrs="showdiscardchangesmessage='false'">
        <div id="testConnectionStatus"></div>
        <div id="testConnectionDetails" class="mono"></div>
      </bs:dialog>
    </td>
  </tr>

</table>