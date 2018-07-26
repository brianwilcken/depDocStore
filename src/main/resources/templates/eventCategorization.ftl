<!DOCTYPE html>
<html>

<head>

    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <title>Classify Event</title>

    <link rel="stylesheet" href="/eventNLP/resources/css/form-labels-on-top.css">

</head>

<body>
<div class="main-content">

    <form class="form-labels-on-top" method="post">

        <div class="form-title-row">
            <h1>Classify Event</h1>
        </div>

        <input type="hidden" name="eventId" value="${eventId}">
        <input type="hidden" name="mode" value="${mode}">

        <div class="form-row">
            <label>
                <span>Title</span>
                <textarea name="title">${title}</textarea>
            </label>
        </div>

        <div class="form-row">
            <label>
                <span>Summary</span>
                <textarea name="summary">${summary}</textarea>
            </label>
        </div>

        <div class="form-row">
            <label>
                <span>Category</span>
                <select name="category">
                    	<#list categories as category>
                            <option value="${category}" <#if (category == eventCategory)>selected="selected"</#if>>${category}</option>
                        </#list>
                </select>
            </label>

        </div>

        <div class="form-row">
            <label>
                <span>Add New Category</span>
                <input type="text" name="newCategory" />
            </label>
        </div>

        <div class="form-row">
            <button type="submit" formaction="/eventNLP/classify">Submit</button>
                <#if (mode == "R")>
                	<button type="submit" formaction="/eventNLP/classify/Next">Next</button>
                	<button type="submit" formaction="/eventNLP/classify/EndReview">End Review</button>
                </#if>
        </div>

    </form>

</div>

</body>

</html>
