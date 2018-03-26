<!DOCTYPE html>
<html>

<head>

	<meta charset="utf-8">
	<meta http-equiv="X-UA-Compatible" content="IE=edge">
	<meta name="viewport" content="width=device-width, initial-scale=1">

	<title>No More Events</title>

	<link rel="stylesheet" href="resources/css/form-labels-on-top.css">

</head>


    <div class="main-content">
    
        <form class="form-labels-on-top" method="post" >

            <div class="form-title-row">
                <h1>No New Events... For Now.</h1>
            </div>
            
            <input type="hidden" name="mode" value="${mode}">
            
            <div class="form-row">
                <button type="submit" formaction="/TrainModel">Train Model</button>
                <button type="submit" formaction="/RefreshEvents">Ping Minute Stream (5 tokens)</button>
                <button type="submit" formaction="/SearchEvents">Broad Search (50+ tokens)</button>
            </div>
            
            <div class="form-row">
                <button type="submit" formaction="/ReviewEvents">Review Events</button>
            </div>
            
            <#if accuracy??>
				<div class="form-row">
					<label>
						<span>Model Performance: ${accuracy}</span>
					</label>
				</div>
			</#if>

        </form>

    </div>

</body>

</html>
