<%@ page import="org.jetbrains.teamcity.vault.VaultConstants" %>
<%@include file="/include-internal.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="context" scope="request" type="jetbrains.buildServer.controllers.parameters.ParameterRenderContext"/>

<c:set var="vaultQuery" value="<%=VaultConstants.ParameterSettings.VAULT_QUERY%>" />

<props:textProperty name="${vaultQuery}" disabled="${context.readOnly}" className="longField" expandable="true" style="width: 100%;"/>

<style type="text/css">
  .posRel {
    width: 100%;
  }
</style>

