<%@ page import="org.jetbrains.teamcity.vault.VaultConstants" %>
<%@include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="context" scope="request" type="jetbrains.buildServer.controllers.parameters.ParameterRenderContext"/>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="hasEditPermission" scope="request" type="java.lang.Boolean"/>

<c:set var="vaultQuery" value="<%=VaultConstants.ParameterSettings.VAULT_QUERY%>" />
<c:set var="readOnly" value="<%=context.isReadOnly() || !hasEditPermission%>" />

<props:textProperty name="${vaultQuery}" disabled="${readOnly}" id="${context.id}" className="longField" expandable="true" style="width: 100%;"/>

<style type="text/css">
  .posRel {
    width: 100%;
  }
</style>

<ext:registerTypedParameterScript context="${context}">
  {
    getControlValue: function() {
      return "vault:" + $('${context.id}').value;
    }
  }
</ext:registerTypedParameterScript>
