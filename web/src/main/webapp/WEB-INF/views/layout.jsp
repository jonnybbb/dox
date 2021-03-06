<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://tiles.apache.org/tags-tiles" prefix="tiles" %>
<%@taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%--
  ~ Copyright 2012 Silvio Wangler (silvio.wangler@gmail.com)
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~        http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<!doctype html>
<!--[if lt IE 7]> <html class="no-js lt-ie9 lt-ie8 lt-ie7" lang="de"> <![endif]-->
<!--[if IE 7]> <html class="no-js lt-ie9 lt-ie8" lang="de"> <![endif]-->
<!--[if IE 8]> <html class="no-js lt-ie9" lang="de"> <![endif]-->
<!--[if gt IE 8]><!-->
<html class="no-js" lang="de"> <!--<![endif]-->
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">

    <title><spring:message code="application.html.title"/></title>
    <meta name="description" content="">
    <meta name="author" content="Silvio Wangler">

    <meta name="viewport" content="width=device-width">

    <link rel="stylesheet" href="resources/css/style.css"/>
    <link rel="stylesheet" href="js/libs/datatables/media/css/jquery.dataTables.css"/>

    <link type="text/plain" rel="author" href="humans.txt"/>
    <script src="js/libs/boilerplate/modernizr-2.5.3.min.js"></script>
</head>
<body>

<div id="container">
    <header>
        <tiles:insertAttribute name="header"/>
    </header>
    <div id="main" role="main">
        <tiles:insertAttribute name="body"/>
    </div>
    <footer>
        <tiles:insertAttribute name="footer"/>
    </footer>
</div>

<script src="//ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js"></script>
<script>window.jQuery || document.write('<script src="js/libs/boilerplate/jquery-1.7.2.min.js"><\/script>')</script>

<script type="text/javascript" language="javascript" src="js/libs/datatables/media/js/jquery.dataTables.js"></script>

<script type="text/javascript">
    $(document).ready(function () {
        $("#docClass").change(function (event) {
            $("#docClassAttributes").load("/ajax/attributes",
                    {
                        documentClassShortName:this.value
                    });
        });
    });

    if ($("#resultTable").length) {
        $('#resultTable').dataTable({
            "bPaginate":false,
            "bLengthChange":false,
            "bFilter":false,
            "bSort":false,
            "bInfo":false,
            "bAutoWidth":false
        });
    }
</script>

</body>
</html>
