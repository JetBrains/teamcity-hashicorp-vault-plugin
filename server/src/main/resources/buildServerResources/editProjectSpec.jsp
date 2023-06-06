<%@ page import="org.jetbrains.teamcity.vault.VaultConstants" %>
<%@include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="context" scope="request" type="jetbrains.buildServer.controllers.parameters.ParameterEditContext"/>
<jsp:useBean id="vaultFeatureSettings" scope="request"
             type="java.util.List<org.jetbrains.teamcity.vault.VaultFeatureSettings>"/>

<c:set var="defaultOption" value="<%=VaultConstants.ParameterSettings.DEFAULT_UI_PARAMETER_NAMESPACE%>"/>
<c:set var="namespaceDropdown" value="<%=VaultConstants.ParameterSettings.NAMESPACE%>"/>
<c:set var="vaultQuery" value="<%=VaultConstants.ParameterSettings.VAULT_QUERY%>"/>

<table class="runnerFormTable">
  <tr>
    <th style="width: 20%"><label for="prop:${namespaceDropdown}">Parameter Namespace: <l:star/></label></th>
    <td>
      <props:selectProperty id="${namespaceDropdown}" name="${namespaceDropdown}" className="longField">
        <props:option value="">-- Please choose namespace --</props:option>
        <c:forEach items="${vaultFeatureSettings}" var="feature">
          <c:choose>
            <c:when test="${empty feature.namespace}">
              <props:option value="${defaultOption}">
                <c:out value="Default Namespace"/>
              </props:option>
            </c:when>
            <c:otherwise>
              <forms:option value="${feature.namespace}">
                <c:out value="${feature.namespace}"/>
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
</table>