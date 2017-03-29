Introduction
============

A common question we encounter is *How does the CA talk to the core?*. This article should hopefully clear up any
lingering doubts around how this communication occurs.  For the purposes of this article the following code conventions
are observed:

  * **$HOST** is your core's base URL.  For example, http://example.org:8080/admin/index.html could also be written
    $HOST/admin/index.html
  * **$AGENT_NAME** is the agent's friendly name.  This is a String.
  * **$RECORDING_ID** is the recording's ID.  This is an Long in the case of a scheduled capture, or a String in the
    case of an unscheduled capture.
  * **$SERIES_ID** is the ID of the series you wish to filter by.
  * **$CUTOFF** is the furthest point in time you want scheduling data for.  This is in seconds (since 1970).


Basic Rules
-----------

  * The core **MUST NOT** attempt to connect to the agent, communication is always agent -> core
  * The agent **MUST** try to send its recording states to the core on a regular basis
  * The agent **SHOULD** send its state and configuration data to the core on a semi regular basis
  * The agent **SHOULD** attempt to update its calendaring data occasionally
  * The agent **MUST** must capture with all available inputs if no inputs are selected (see 1a in the quick overview)
  * The agent **SHOULD**, however, tell the core its address so that this can be used to pull down confidence
    monitoring information to a client machine (eg, the console in a classroom).

Quick Overview
==============

The following list gives a short overview over the commubication between the CA and the Core. Remember, it is up to the
CA to do the following work. The Core will only provide the schedule.

**Starting the CA**
 
|    |Task       |Request                                 |Data                             |Notes                    |
|----|-----------|----------------------------------------|---------------------------------|-------------------------|
|1a. |Register   |POST<br/>/capture-admin/agents/$AGENT_NAME  |- 'address' : 'URL of your CAs UI'<br/> - 'state' : 'idle'| This will register an agent without any capabilities (inputs).  It will be schedulable, but no input selection will be possible.  An agent without listed capabilities must capture with all inputs when scheduled|
|1b. |Register   |POST<br/>/capture-admin/agents/$AGENT_NAME/configuration | - configuration: An XML representation of the capabilities, as specified in http://java.sun.com/dtd/properties.dtd (friendly names as keys, device locations as their corresponding values)| If you execute this before 1a you will register an agent without a state, but with capabilities (inputs).  It will be schedulable, but the scheduling UI will complain that the agent might be offline.|
|2.	|Get Schedule|GET<br/> /recordings/calendars?agentid=$AGENT_NAME | - cutoff: an optional parameter which is the time since epoch at which the scheduler endpoint should cut the schedule off.  Useful for memory limited devices who may not want every upcoming event.  Note: time is UTC. |This should happen on a regular basis (default of 5 minutes).  See below for more information. Schedule has ETags.|

**After a schedules date has arrived, start recording the video...**
 
|    |Task               |Request                                     |Data                                           |
|----|-------------------|--------------------------------------------|-----------------------------------------------|
|3.  |Set CA state       |POST /capture-admin/agents/$AGENT_NAME      |'address' : 'URL of your CAs UI'<br/>'state' : 'capturing'|
|4.  |Set recording state|POST /capture-admin/recordings/$RECORDING_ID|'state' : 'capturing'|

**Now you stop recording the video...**

|    |Task               |Request                                     |Data                                           |
|----|-------------------|--------------------------------------------|-----------------------------------------------|
|7.  |Set CA state       |POST /capture-admin/agents/$AGENT_NAME      |'address' : 'URL of your CAs UI'<br/>'state' : 'idle'|
|8a. |Set recording state|POST /capture-admin/recordings/$RECORDING_ID|'state' : 'manifest'                           |
|8b. |Create the manifest|Generate your manifest file (only needed for zipped ingest, see 9b)|                        |
|9a  |Set recording state|POST /capture-admin/recordings/$RECORDING_ID|'state' : 'upload'                             |
|9b. |Ingest media       |There are currently three different methods to ingest media:<br/>Single request ingest: POST /ingest/addMediaPackage<br/>Zipped media ingest: POST /ingest/addZippedMediaPackage<br/>Mulit request ingest: POST /ingest/createMediaPackage<br/>POST /ingest/addDCCatalog<br/>POST /ingest/addTrack<br/>POST /ingest/ingest|Tracks<br/>Mediapackages<br/>Dubline Core metadata|
|10. |Set recording state|POST /capture-admin/recordings/$RECORDING_ID|'state' : 'upload_finished'|

**Explanation:**

  1. Register the CA at the core. You do that by sending a POST request to containing the URL of the admin ui for your
     Capture Agent and its current status. The status should be set to “idle” as your CA is not recording anything at
     the moment.
  2. Request the schedule for your CA from the Matterhorn Core server. This will return a vCalendar containing all
     future dates for recordings, the recording IDs, … You should update this regularly. Recommended is an update
     every one to five minutes. If you do that more frequently, you put more stress on your Core server. If you do it
     less frequently you might miss last minute schedules.
  3. Change the CA state to make clear that the CA started to record a video.  This state update should be very
     regular, and frequent.  Ideally every minute or so, although longer timeframes are allowed and will work.
  4. Change the state of a recording to indicate that this recording is processed. This state update should be very
     regular, and frequent.  Ideally every minute or so, although longer timeframes are allowed.  If the initial state
     update does not take place quickly enough, the Core will mark the recording as “might have failed” after a couple
     of minutes.  This will be rectified upon the first state update, however it does concern administrators when their
     captures appear to have failed.  The recording id necessary for this request is part of the schedule data.
  5. Once capture has finished, change your CA state. It's now idle (the uploading operation is a recording-level
     event).
  6. Change the recording's state to uploading.
  7. Ingest the media. There are currently three ways to do this:
    1.  *Single request ingest:* You can put everything in one single request. The good point about this is that you
        have to do only a single action. The bad point is that this request might become quite large. So the
        possibility of a failure might be higher than the multi reqquest ingest if you are in an unstable network.
    2. *Zipped media ingest:* It is basically like the single request ingest, only you put all data in one big,
       uncompressed zipfile. The advantage of this is, that you basically cannot mess up the request because you are
       doing something wrong as the request order, etc. is irrelevant for this.
    3. *Multi file ingest:* You send all files to the Core separately, one after another and tell the Core at the end,
       that you are finished. You have to do more than one request this way, but the request itself are smaller.
  8. Change the recordings state to upload_finished. 
 
**Notes:**

  * States are required to match known Matterhorn states (linked below).
  * Many states have failure versions as well, these are used while the agent waits to retry.
 
Agent State And Configuration
=============================

**Creating An Agent On The Core**

An agent record is created on the core the first time the agent communicates with the core. There is no special
endpoint or registration required, just send the state (although sending the configuration data works as well) and the
agent record will be created.

Note that an agent which only has configuration data may not be scheduleable in the administration ui, and an agent
with no configuration data will use its default inputs when the recording starts.  Also note that, in this case, the
recording may not occur at the correct time if the agent is in a different timezone than the core.

**Agent State**

The agent should send its state on a regular interval. By default this occurs every 10 seconds. The agent's state is
not terribly important (yet), so slower intervals for this type of update are not necessarily a bad thing. Future
changes to the administration UI may disable agents which have not reported their states recently enough.

To send the agent's state to the core a valid state (as defined here) is sent via HTTP POST to
$HOST/capture-admin/agents/$AGENT_NAME

**Agent Configuration**

The agent **SHOULD** send its configuration data on a regular interval, although this is not required. By default this
occurs every 10 seconds.  The agent's configuration data need only be transmitted:

  * When the agent makes contact with the core for the first time (N.B:  This includes cases where the core has been
    reset and has lost its records of the agent).
  * When the agent's configuration changes.  This includes timezone changes (but not DST changes).

Incorrect configuration data can result in captures which fail or occur at an incorrect time so therefore a slow, but
regular update interval is recommended.

To send the agent's configuration to the core an XML structure which can deserialize into a Java Properties object is
sent via HTTP POST to $HOST/capture-admin/agents/$AGENT_NAME/configuration

The format of this XML structure is the following:

    <?xml version="1.0" encoding="UTF-8" standalone="no"?>
    <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd\">
    <properties>
        <comment>Capabilities for $AGENT_NAME</comment>
        <entry key="$KEY_NAME">$VALUE</entry>
        ...
    </properties>

The following are very important keys:

|Key Name                     |Example Value                               |Description                               |
|-----------------------------|--------------------------------------------|------------------------------------------|
|capture.device.names         |MOCK_SCREEN, MOCK_PRESENTER, MOCK_MICROPHONE| A list of friendly names for the inputs available for this agent. This list can be used to disable individual inputs on a recording-by-recording basis. Agents with onboard configuration tools (like the ECD) are allowed to override these settings.|
|capture.device.$DEVICE_NAME.*|                                            |Detailed configuration data for the input. Examples are bitrate, framerate, and container type. These details are displayed in the agent's dropdown in the administration ui.  See examples in the default capture agent configuration file.|

Recording State
---------------

**Creating a recording on the core**

There are two places where a "recording" is tracked in the core.  The first, and most prominent, is the
workflow/Recording tab in the administration UI.  The capture agent has no directly communication with the services
backing that UI.  Instead, it communicates with the capture-admin APIs which only track the ID, state and
time-since-last-checkin (the workflow UI uses this information for some of the workflow states).  Creating a recording
in the capture-admin APIs does not create a capture in the administration UI, nor vice versa.  Recordings in the
capture-admin APIs are created the first time the agent communicates the status of a recording.

Agents must send each recording's state to the core on a regular basis.  By default this occurs every 10 seconds.
The reference agent sends the state of every recording is has on disk, regardless of whether that recording has been
ingested or not, however this is not required.  The bare minimum is sending the recording's state while it is active
(capture through ingest) within the agent.  Depending on the implementation the agent could be sending many state
updates, and therefore create a heavy load on the core.  Slower intervals are allowed, however keep in mind that these
intervals also affect the state displayed in the workflow/Recording UI.  Slower intervals mean a higher likelihood that
the administration UI will temporarily display a warning that the capture failed to start (thus alarming your
administrators unnecessarily).

To send the recording's state to the core a valid state (as defined here) is sent via HTTP POST to
$HOST/capture-admin/recordings/$RECORDING_ID

**Calendaring Data**

The agent is expected to understand the Matterhorn core icalendar implementation, however there is no hard requirement
for the agent to poll the core for updated information.  As an example, the reference calendar agent will schedule any
current or future events in its cached calendar (a file on disk) on startup.  It will then periodically poll for
updated calendaring information, schedule those captures in RAM, and then cache the update in case of power failure.
This allows the agent to be used in a network-less environment:  Merely precache the calendar data, or allow the agent
to fetch the calendar once.  The agent will then capture the scheduled recordings (power permitting) and cache those
for later ingestion.

The above scenario is the reference, however there is no hard requirement for local capture caching or local schedule
caching.

To retrieve the calendar for an agent an HTTP GET is performed to
$HOST/recordings/calendars?agentid=$AGENT_NAME&seriesid=$SERIES_ID&cutoff=$CUTOFF

Note that the schedule has ETag support, which is very useful to speed up the processing of larger calendars.

**Preparing The Metadata**

**Dublin Core Catalogs**

Matterhorn captures are encouraged to have a Dublin Core catalog which contains much of the metadata for the capture.
The following the the basic Dublin Core format we use:

    <?xml version="1.0" encoding="UTF-8" standalone="no"?>
    <dublincore
            xmlns="http://www.opencastproject.org/xsd/1.0/dublincore/"
            xmlns:dcterms="http://purl.org/dc/terms/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <$KEY>$VALUE</$KEY>
        ...
    </dublincore>

The following are very important keys:

|Key Name                                 |Example Value                               |Description                   |
|-----------------------------------------|--------------------------------------------|------------------------------|
|dcterms:created xsi:type="dcterms:W3CDTF"|2012-05-22T22:48:41.786Z                    |The time at which the capture was scheduled.|
|dcterms:identifier                       |Unscheduled-demo_capture_agent-1337726921786|The internal, unique capture identifier. This can be omitted for unscheduled captures.|
|dcterms:title                            |Unscheduled-demo_capture_agent-1337726921786|The title for the capture, and does not need to be unique. This file should be called episode.xml.|

**Capture Agent Configuration File**

*This is only important if you use the Zipped File Ingest method!*

When your CA decodes the attachments in a scheduled capture there should be two files. The contents of the episode.xml
file should be similar (if more extensive) to the description above. The other file,
org.opencastproject.capture.agent.properties, contains the capture agent configuration directives (turning inputs on
and off, for example) as well as workflow directives. These workflow directives specify highly important directives
without which the core may misbehave. Workflow directives are prefixed with org.opencastproject.workflow.config. For
example, the configuration file below instructs the core to hold the capture from trimming. When passing the
configuration directive to the core, the CA must strip this prefix from the parameter. For example,
org.opencastproject.workflow.config.trimHold=true is passed to the core as trimHold=true. The
org.opencastproject.workflow.definition directive is important as well, this is the workflow definition ID and should
be passed as a parameter during the actual ingest operation.

    #Capture Agent specific data
    #Tue May 22 17:34:22 CST 2012
    org.opencastproject.workflow.config.trimHold=true
    capture.device.names=MOCK_SCREEN,MOCK_PRESENTER,MOCK_MICROPHONE
    org.opencastproject.workflow.definition=full
    event.title=Test Capture
    event.location=demo_capture_agent
	
**Ingest**

*Zipped Media Ingest*

Zip based ingest is the original method that the reference CAs used to ingest media. Its use for new CA implementations
is, however, discouraged because of the heavy load it places on the core (or admin node). The captured media, along
with some metadata files is zipped (with or without compression) and then HTTP POSTed to the core. The core then
unzips the mediapackage and begins processing. This unzipping operation is quite disk intensive, and the REST endpoint
does not return until the unzipping is done. This has led to many reports of proxy timeouts and excessive disk
utilization.

To ingest a zipped media package an HTTP POST is performed to $HOST/ingest/addZippedMediaPackage. The BODY of the
POST must contain the zipped media package. There are two optional form parameters are well. The workflowDefinitionId
parameter defines the ID of the workflow which should be used to process the zipped media package. The default for
this parameter is defined in your core's configuration file, and is at the time of this writing "full". The
workflowInstanceId parameter is the internal ID of the capture. This should match the identifier field in the Dublin
Core metadata file. Ingesting a capture without this parameter will create a new workflow (ie, a new row) in the admin
UI. This is the correct behaviour if you have an unscheduled capture (ie, adhoc and therefore won't have a workflow ID
to match), however for scheduled captures this should match the scheduled capture's ID, as well as the identifier
 field in the Dublin Core. Workflow configuration parameters, if any, can be passed as addition form parameters.

*Single Request Ingest*

The single request ingest puts less stress on both the CA and the Core as the media media does not have to be packed
and extracted anymore. The request is, however, a little bit more tricky and if something goes wrong still the whole
reqtest fails.

You do a single request ingest by posting all data to the $HOST/ingest/addMediaPackage REST endpoint. You should be
aware that the order of the POST fields is important. For example, you have to tell the Core first the flavor of a
video and then put the video itself into the request. The request will fail or produce undesired results otherwise.

*Multi Request Ingest*

REST based ingest is more complicated than Zip based, however its use is encouraged because of its superior speed and
reliability.

A number of HTTP calls are made during the ingestion of a mediapackage. The result of a successful call is the newly
updated mediapackage, while the result of a failed call is usually an HTTP 400 or 500. This mediapackage is created by
one call, then amended by a number of other calls, then finally passed to the last endpoint to begin processing. Thus,
it is vitally important that the mediapackage from each call be preserved from one to the next.

To begin, the CA must first generate a valid base mediapackage. This is done by making an HTTP GET request to
$HOST/ingest/createMediaPackage. The resulting mediapackage will be blank, but contain the base skeleton used in later
calls.

The next step(s) vary depending on your desired implementation. Each file in the capture must be added, one at a time to the mediapackage. The order of these operations is left up to the implementor.

*Tracks*

To add a track to the mediapackage an HTTP POST is sent to $HOST/ingest/addTrack. The BODY of the POST must contain the
bare video file. There are also two other required form parameters. The flavor parameter specifies what flavour the
track contains. Examples include "presentation/source" or "presenter/source". Third party CAs may generate other
flavors, and the core's workflows may require modification to make use of these files. Files with unrecognized flavors
are ignored. The mediapackage form parameter must be the mediapackage generated by the last HTTP call.

*Catalogs*

To add a Dublin Core catalog to the mediapackage an HTTP POST is sent to $HOST/ingest/addDCCatalog. The BODY of the
POST must contain the bare Dublin Core file. There are also two other required form parameters. The flavor parameter
specifies what flavour the catalog is. Examples include "dublincore/episode" for the episode catalog or
"dublincore/series" for the series catalog. Other catalogs may be added, however the core will likely ignore them.
These extra catalogs can also safely be added as attachments. The mediapackage parameter must be the mediapackage
generated by the last HTTP call.

*Attachments*

To add attachments to the mediapackage an HTTP POST is sent to $HOST/ingest/addAttachment. The BODY of the POST must
contain the bare file. There are also two other required form parameters. The flavor parameter specifies what flavour
the file is. These files may be added, however the core will ignore them. Attachments are typically files which should
not be processed but are needed for the engage player, or third party integration (eg: permissions files for your LCMS).
The mediapackage parameter must be the mediapackage generated by the last HTTP call.

*Ingest*

Once you have added all of the capture's files it is time to ingest the mediapackage and begin processing. At this
point processing will begin, and no further files can be added to the mediapackage.

To ingest a recording, an HTTP POST is sent to $HOST/ingest/ingest. There is one required form parameter, and an
arbitrary number of optional form parameters. The required parameter is the mediaPackage parameter, and must be the
mediapackage generated by the last HTTP call. The workflowDefinitionId parameter defines the ID of the workflow which
should be used to process the media package. The default for this parameter is defined in your core's configuration
file, and is at the time of this writing "full". The workflowInstanceId parameter is the internal ID of the capture.
This should match the identifier field in the Dublin Core metadata file. Ingesting a capture without this parameter
will create a new workflow (ie, a new row) in the admin UI. This is the correct behaviour if you have an unscheduled
capture (ie, adhoc and therefore won't have a workflow ID to match), however for scheduled captures this should match
the scheduled capture's ID, as well as the identifier field in the Dublin Core. Workflow configuration parameters,
if any, can be passed as addition form parameters.
