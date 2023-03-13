# WhatsApp-to-DHIS2 Polling Demo
In the following demo for DHIS-to-RapidPro, we will connect a RapidPro instance with a DHIS2 Instance and enable report polling.

## Requirements

* Java 11
* RapidPro v7.4
* DHIS >= v2.36.12
* Windows OS (For Mac and *inux instructions, go to the original [Github Page](https://github.com/dhis2/integration-dhis-rapidpro))

## Getting Started

### Downloading the Jar Distribution

The [JAR distribution](https://github.com/dhis2/integration-dhis-rapidpro/releases) of DHIS-to-RapidPro allows you to run the application as a stand-alone process. On windows, you can execute DHIS-to-RapidPro from your terminal like so:
```shell
java -jar dhis2-to-rapidpro.jar
```

The above command will give an error since no parameters are provided. Before we can use the dhis-to-rapidpro connector, we need to configure a DHIS2 data set and RapidPro flow. 

### Aggregate Report Transfer

Follow the subsequent DHIS2 and RapidPro setup instructions to be able to transfer aggregate reports from RapidPro to DHIS2.

#### DHIS2 Instructions

1. Configure codes for the data sets that the reports transmitted from RapidPro to DHIS-to-RapidPro will target. To configure the data set code:
    1. Go to the maintenance app
    2. Open the data sets page
    3. Search for the data set
    4. Enter a suitable code in the _Code_ field as shown next:
       ![Data set form](static/images/dhis2-data-set.png)
       >**IMPORTANT:** you need to enter a code that starts with a letter, a hyphen, an underscore, or a whitespace to achieve successful interoperability between DHIS2 and RapidPro. Special characters that are not permitted in a RapidPro result name should NOT be part of the code. Hyphens, underscores, and whitespaces are typically permitted.

2. Configure a code in each data element that will capture an aggregate value from RapidPro. To configure the data element code:
   1. Go to the maintenance app
   2. Open the data elements page
   3. Search for the data element
   4. Enter a suitable code in the _Code_ field as shown next:
      ![Data element form](static/images/dhis2-data-element.png)
      >**IMPORTANT:** you need to enter a code that starts with a letter, a hyphen, an underscore, or a whitespace to achieve successful interoperability between DHIS2 and RapidPro. Special characters that are not permitted in a RapidPro result name should NOT be part of the code. Hyphens, underscores, and whitespaces are typically permitted.

#### RapidPro Instructions

In this Demo, DHIS-to-RapidPro will ingest aggregate reports from RapidPro as
completed flow executions that are retrieved while polling the RapidPro API. The next section describes the flow configuration needed to enable aggregate report transfer. 

##### Polling

1. Open a RapidPro flow definition that processes the contact's report or create a new flow definition.
2. Configure a trigger keyboard for the flow. This can be done under the trigger tab in your RapidPro instance. 

3. Identify the root of each happy flow path, that is, the root of each successful execution path. You should apply the proceeding steps to these root paths. 

4. Save a result containing the DHIS2 code of the data set representing the report:
 
    <img src="static/images/data-set-code-poll.png" width="50%" height="50%"/>

   Type the result name `data_set_code` and give it as a value the code of the data set as retrieved from DHIS2's maintenance app.

5. Save each incoming report value to a result as per the example shown next:

    <img src="static/images/opd-attendance.png" width="50%" height="50%"/>

   The result name must match the code of the corresponding data element in DHIS2. Upper case letters in the data element code can be entered as lower case letters in the result name field while whitespaces and hyphens can be entered as underscores If a category option combination is required, suffix the result name with two underscores and append the category option combination code to the suffix:

    <img src="static/images/opd-attendance-category.png" width="50%" height="50%"/>

6. Optionally, save a result which contains the report period offset:

    <img src="static/images/report-period-offset-poll.png" width="50%" height="50%"/>

    Type the result name `report_period_offset` and give it as a value the relative period to add or subtract from the current reporting period sent to DHIS2. If omitted, the report period offset defaults to -1.

7. Copy the UUID of the flow definition from your web browser's address bar:
   ![browser address bar](static/images/flow-uuid-poll.png)

8. Paste the copied flow definition UUID into DHIS-to-RapidPro's `rapidpro.flow.uuids` config property. For example:

    ```shell
    java -jar dhis2rapidpro.jar \ 
   --dhis2.api.url=http://192.46.212.72:8080/sjmch/api \ 
   --rapidpro.api.url=https://textit.com/api/v2 \
   --rapidpro.flow.uuids=e142896b-ba68-4bba-9415-9b0d900afb93
    ```
    
    You can poll multiple flows by having the flow UUIDs comma separated:

   ```shell
    java -jar dhis2rapidpro.jar \ 
   --dhis2.api.url=http://192.46.212.72:8080/sjmch/api \ 
   --rapidpro.api.url=https://textit.com/api/v2 \
   --rapidpro.flow.uuids=e142896b-ba68-4bba-9415-9b0d900afb93,e142896b-ba68-4bba-9415-9b0d900afb95,e142896b-ba68-4bba-9415-9b0d900afb22
    ```

    >NOTE: `scan.reports.schedule.expression` config property determines how often flow executions are polled.

While DHIS-to-RapidPro is running, to manually kick off the scanning of flow runs:

1. Open your web browser
2. Type the DHIS-to-RapidPro URL together with the path `/services/tasks/scan` inside the browser address bar
3. Press enter

#### Running DHIS-to-rapidpro
Now that the RapidPro flow and DHIS2 data set is properly configured, we can now run the dhis-to-rapidpro connector. Before we run the jar distribution, we need to set some OS environment variables:

```shell
    set DHIS2_API_USERNAME=admin
    set DHIS2_API_PASSWORD=secret-password
    set RAPIDPRO_API_TOKEN=secret-api-token
```
We can now run the jar distribution, passing in some command-line arguments to enable polling. Note that the rapidpro.flow.uuids must correspond to the flow uuid that you want to poll. 

```shell
    java -jar dhis2rapidpro.jar \ 
   --dhis2.api.url=http://192.46.212.72:8080/sjmch/api \ 
   --rapidpro.api.url=https://textit.com/api/v2 \
   --rapidpro.flow.uuids=e142896b-ba68-4bba-9415-9b0d900afb93
```
You are now all set! THe last step is to connect to the WhatsApp endpoint. 

### Testing using WhatsApp
To test the aggregate report transfer using polling, you need to send a message to the WhatsApp chatbot: `+1 (339) 675-2781`. The first message must contain the trigger keyword. 
The whatsApp chatbot will now instantiate a new flow based on the trigger keyword. 

<img src="static/images/whatsapp-trigger-keyword.png" width="50%" height="50%"/>
