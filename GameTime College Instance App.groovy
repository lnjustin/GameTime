/**
 *  GameTime College Child
 *
 *  Copyright 2021 Justin Leonard
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *  v1.2.0 - Full Feature Beta
 *  v1.2.1 - Bug fixes
 *  v1.2.2 - Update scheduling if late night game; Time Formatting improvements
 *  v1.2.3 - Bug fixes
 *  v1.2.4 - Added option to hide game result spoilers
 *  v1.2.5 - Bug fixes
 *  v1.2.6 - Bug fixes
 *  v1.2.7 - Hide record when hide game spoilers
 *  v1.3.0 - Added option to designate team as Low Priority
 *  v1.3.1 - Fixed issue with NFL bye weeks
 *  v1.4.0 - Added schedule attribute
 *  v1.4.1 - Fixed issue with schedule attribute displaying on native hubitat dashboards
 *  v1.4.2 - Bug fix with college schedule tile
 *  v1.4.3 - Improved schedule tile display
 *  v1.5.0 - Improved api key input, added event notifications
 *  v1.5.1 - Fixes issue with pregame event notifications when next game cancelled
 *  v1.5.2 - Fixes issue with updating tile after the last game of the season
 *  v1.5.3 - Fixes issue with tile font size configurability
 *  v1.5.4 - Added Uninstall Confirmation; Added Update Interval Configurability
 *  v1.5.5 - Added ability to configure tile text color from parent GameTime device
 *  v1.5.6 - Fixed issue with NFL post season
 *  v1.5.7 - Gracefully handle unauthorized API access
 */
import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.time.TimeCategory

definition(
    name: "GameTime College Instance",
    parent: "lnjustin:GameTime",
    namespace: "lnjustin",
    author: "Justin Leonard",
    description: "GameTime Tracker for College and Professional Sports",
    category: "My Apps",
    oauth: [displayName: "GameTime", displayLink: ""],
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

@Field String checkMark = "https://raw.githubusercontent.com/lnjustin/App-Images/master/checkMark.svg"
@Field String xMark = "https://raw.githubusercontent.com/lnjustin/App-Images/master/xMark.svg"

preferences {
    page name: "mainPage", title: "", install: true, uninstall: true

}

@Field leagues = ["College Football", "Men's College Basketball", "Women's College Basketball"]
@Field api = ["College Football":"cfb", "Men's College Basketball":"cbb", "Women's College Basketball":"wcbb"]

mappings
{
    path("/gametime/:appId") { action: [ GET: "fetchSchedule"] }
}

def getScheduleEndpoint() {
    return getFullApiServerUrl() + "/gametime/${app.id}?access_token=${state.accessToken}"    
}

def getUpdateInterval() {
    return settings['updateInterval'] != null ? settings['updateInterval'] : 600
}

def instantiateToken() {
     if(!state.accessToken){	
         //enable OAuth in the app settings or this call will fail
         createAccessToken()	
     }   
}

def mainPage() {
    dynamicPage(name: "mainPage") {
       
            def key = getAPIKey()
            section {
              //  header()                
                paragraph getInterface("header", " GameTime College Instance")
                paragraph getInterface("note", "After selecting the league and your team, click DONE. This will create a device for the selected team, listed under the GameTime parent device.")
                input(name:"league", type: "enum", title: "College Sports League", options: leagues, required:true, submitOnChange:true)
                if(!key && league) {
                    def link = getInterface("link", "SportsData.IO API Key", "https://sportsdata.io/")
                    def inputTitle = link + " for ${league}"
                    input(name:"apiKey", type: "text", title: inputTitle, required:true, submitOnChange:true)
                }
                else if (key && league) {
                    if (!state.teams) setTeams()
                    input(name:"conference", type: "enum", title: "Conference", options: getConferenceOptions(), required:true, submitOnChange:true)
                    if (conference) {
                        input(name:"team", type: "enum", title: "Team", options: getTeamOptions(), required:true, submitOnChange:true)
                    }
                }
                
            }
            if (team) {
                section (getInterface("header", " Event Handling")) {  
                    paragraph getInterface("subHeader", " First Pre-Game Event")
                    paragraph getInterface("note", "Button 1 will be pushed upon the first pre-game event.") 
                    input name: "firstEventAdvance", type: "number", title: "First Pre-Game Event Occurs How Many Minutes Before GameTime?", defaultValue: 60
                    input name: "isFirstEventNotify", title:"Send Push Notification?", type:"bool", required:false, submitOnChange:false, defaultValue: false
                    paragraph getInterface("subHeader", " Second Pre-Game Event")
                    paragraph getInterface("note", "Button 2 will be pushed upon the second pre-game event.") 
                    input name: "secondEventAdvance", type: "number", title: "Second Pre-Game Event Occurs How Many Minutes Before GameTime?", defaultValue: 0
                    input name: "isSecondEventNotify", title:"Send Push Notification?", type:"bool", required:false, submitOnChange:false, defaultValue: false
                    paragraph getInterface("subHeader", " Win Event")
                    paragraph getInterface("note", "Button 3 will be pushed upon a win.") 
                    input name: "isWinEventNotify", title:"Send Push Notification?", type:"bool", required:false, submitOnChange:false, defaultValue: false
                    paragraph getInterface("subHeader", " Loss Event")
                    paragraph getInterface("note", "Button 4 will be pushed upon a loss.")
                    input name: "isLossEventNotify", title:"Send Push Notification?", type:"bool", required:false, submitOnChange:false, defaultValue: false
                    input name: "notificationDevices", type: "capability.notification", title: "Devices to Notify", required: false, multiple: true, submitOnChange: false
                }
            }
            section (getInterface("header", " Settings")) {
                input name: "updateInterval", type: "number", title: "Update Interval While Game In Progress (mins)", defaultValue: 10
                if (team) input name: "hideGameResult", title:"Hide Game Result?", type:"bool", required:false, submitOnChange:false
                if (team) {
                    input name: "lowPriority", title:"Low Priority Team?", type:"bool", required:false, submitOnChange:false
                    input name: "priorityHourThreshold", type: "number", title: "Low Priority Team Hour Threshold", defaultValue: 24
                    paragraph getInterface("note", "A low priority team will only display on the 'all teams' GameTime device if no higher priority team has a game within X hours. The Low Priority Team Hour Threshold specifies X. If you change the priority status of a team after install, you must go to the parent app and click DONE in order for the prioritzation change to have immediate effect.") 
                }
                input("clearStateBetweenUpdate", "bool", title: "Clear teams data between updates?", defaultValue: true, required: false)
                paragraph getInterface("note", "Enabling Clear 'Teams Data Between Updates' reduces state size. Disabling conserves API calls.")
                if (team) input name: "numGamesForSchedule", type: "number", title: "Num Games For Schedule Tile", defaultValue: 3
                if (team) label title: "GameTime Instance Name", defaultValue: team + " GameTime Instance", required:false, submitOnChange:true
                if (key && league) {
                    def link = getInterface("link", "SportsData.IO API Key", "https://sportsdata.io/")
                    def inputTitle = link + " for ${league}"
                    input(name:"apiKey", type: "text", title: inputTitle, required:false, submitOnChange:true, defaultValue: key)
                }
			    input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false)
		    }
            section("") {
                
                footer()
            }
    }
}

def getLeagueAPIKey(forLeague) {
    def key = null
    if (league == forLeague) key = apiKey
    return key
}
                         
def getAPIKey() {
    def key = null
    if (apiKey) key = apiKey
    else if (league != null) key = parent.getLeagueAPIKey(app.id, league)
    return key
}

Integer getNumGamesForScheduleSetting() {
    return numGamesForSchedule != null ? (int) numGamesForSchedule : 3
}

Integer getFirstEventAdvanceSetting() {
    return firstEventAdvance != null ? (int) firstEventAdvance : 60
}

Integer getSecondEventAdvanceSetting() {
    return secondEventAdvance != null ? (int) secondEventAdvance : 0
}

def getLowPriorityThresholdSetting() {
    return priorityHourThreshold != null ? priorityHourThreshold : 24
}

def getLowPrioritySetting() {
    return lowPriority ? lowPriority : false
}


def getFontSizeSetting() {
    return parent.getFontSizeSetting()
}

def getHideGameResultSetting() {
    return hideGameResult ? hideGameResult : false
}

def getScheduleFontSizeSetting() {
    return parent.getScheduleTileFontSize()
}

def getScheduleTextColorSetting(oddOrEven) {
    return parent.getScheduleTileTextColor(oddOrEven)
}

def getScheduleBackgroundColorSetting(oddOrEven) {
    return parent.getScheduleTileBackgroundColor(oddOrEven)
}

def getTextColorSetting() {
    return parent.getTextColorSetting()
}

def getClearStateSetting() {
    return clearStateBetweenUpdate != null ? clearStateBetweenUpdate : true
}

def getInactivityThresholdSetting() {
    return parent.getInactivityThresholdSetting()
}

def getClearWhenInactiveSetting() {    
    return parent.getClearWhenInactiveSetting()
}

def getTeamKey() {
    return state.team?.key  
}

def footer() {
    paragraph getInterface("line", "") + '<div style="display: block;margin-left: auto;margin-right: auto;text-align:center">&copy; 2020 Justin Leonard.<br>'
}
    
def installed() {
	initialize()
}

def updated() {
    unschedule()
	unsubscribe()
    def storedRecord = state.lastRecord
    state.clear()
    if (storedRecord != null) state.lastRecord = storedRecord
	initialize()
}

def uninstalled() {
    deleteChild()
	logDebug "Uninstalled app"
}

def initialize() {
    instantiateToken()
    def key = getAPIKey()
    if (league && team && key) {
        setTeams()
        setMyTeam()
        createChild()
        update(true)
        schedule("01 01 00 ? * *", scheduledUpdate)
    }
    else log.error "Missing input fields."
}

def scheduledUpdate()
{
    update(true)    
}

def setTeams() {
   if (!state.teams) state.teams = [:]
   def fullTeams = fetchTeams()
   for (tm in fullTeams) {
       def displayName = tm.School + " " + tm.Name
       def teamMap = [id: tm.TeamID, key: tm.Key, school: tm.School, name: tm.Name, logo: tm.TeamLogoUrl, displayName: tm.ShortDisplayName, rank: tm.ApRank, wins: getIntValue(tm.Wins), losses: getIntValue(tm.Losses), conference: tm.Conference]
       state.teams[tm.Key] = teamMap
   }
}

def setStandings() {
   def fullTeams = fetchTeams()
   for (tm in fullTeams) {
       state.teams[tm.Key]?.wins = getIntValue(tm.Wins)
       state.teams[tm.Key]?.losses = getIntValue(tm.Losses)
       state.teams[tm.Key]?.rank = tm.ApRank
       if (tm.Key == state.team.key) {
           state.team = state.teams[tm.Key]
       }
   }
}

def getIntValue(standingComponent) {
    def value = 0   
    if (standingComponent != null && standingComponent != "null") {
        value = standingComponent as Integer
    }
    return value
}

Date getDateObj(dateStr) {
    // accepts input in format "yyyy-MM-dd'T'HH:mm:ss", without time zone information. Assumes eastern time zone already adjusted for DST
    def str = dateStr
   def isDST = TimeZone.getDefault().inDaylightTime( new Date() )
    if (isDST) str = str + "-04:00"
    else str = str + "-05:00"    
    def dateObj = toDateTime(str)
    return dateObj
}

def isToday(Date date) {
    def isToday = false
    if (date != null) {
        def today = new Date().clearTime()
        def dateCopy = new Date(date.getTime())
        def dateObj = dateCopy.clearTime()
        if (dateObj.equals(today)) isToday = true
    }
    return isToday
}

def isYesterday(Date date) {
    def isYesterday = false
    def today = new Date().clearTime()
    def yesterday = today - 1
    def dateCopy = new Date(date.getTime())
    def dateObj = dateCopy.clearTime()    
    if (dateObj.equals(yesterday)) isYesterday = true
    return isYesterday
}

def update(onInitialize = false) {
    logDebug("Updating GameTime for ${state.team.displayName}")
    if (!state.teams) setTeams()
    updateState(onInitialize)
    updateDisplayedGame()
    scheduleUpdate()
}

def getScheduleTile() {  
    logDebug("Getting schedule tile for ${state.team.displayName}")
    if (!state.refreshNum) state.refreshNum = 0
    state.refreshNum++
    def scheduleUrl = getScheduleEndpoint() + '&version=' + state.refreshNum   
        
    def scheduleTile =     "<div style='height:100%;width:100%'><iframe src='${scheduleUrl}' style='height:100%;width:100%;border:none'></iframe></div>"
    return scheduleTile
}

def fetchSchedule() {
    logDebug("Fetching Schedule")
    if(params.appId.toInteger() != app.id) {
        logDebug("Returning null since app ID received at endpoint is ${params.appId.toInteger()} whereas the app ID of this app is ${app.id}")
        return null    // request was not for this app/team, so return null
    }
    
    def scheduleTile = "<div style='height:100%'></div>"
    def oddTextColor = getScheduleTextColorSetting("odd")
    def oddBackgroundColor = getScheduleBackgroundColorSetting("odd")
    def evenTextColor = getScheduleTextColorSetting("even")
    def evenBackgroundColor = getScheduleBackgroundColorSetting("even")
    def fontSize = getScheduleFontSizeSetting()
    if (state.schedule != null) {
        scheduleTile = "<div style='height:100%;'><table width='100%' style='border-collapse: collapse; font-size:${fontSize}%;'>"
        def numRows = 0
        def numGames = state.schedule.size()
        for (game in state.schedule) {
            def backgroundColor = numRows % 2 == 0 ? evenBackgroundColor : oddBackgroundColor
            def textColor = numRows % 2 == 0 ? evenTextColor : oddTextColor
            scheduleTile += "<tr width='100%' height='${100/numGames}%' style='background-color:${backgroundColor}; color: ${textColor}'><td width='25%' style='margin:0; padding:4' align=left>${getGameDayOfWeek(game.gameTime)} <b>${getGameDate(game.gameTime)}</b></td>"
            scheduleTile += "<td width='100%' style='padding:4;display:flex; align-items:center; justify-content: center;' align=center>" + (game.homeOrAway == "home" ? "vs " : "@ ") + "<img src='${game.opponentLogo}' height='" + 25*(fontSize/100) + "vh' style='padding:4'> ${game.opponent}</td>"
            scheduleTile += "<td width='25%' style='padding:4;margin:0' align=right>${getGameTimeOfDay(game.gameTime)}</td></tr>"
            numRows++
        }
        scheduleTile += "</table></div>" 
    }
    logDebug("Calling render on ${scheduleTile}")
    render contentType: "text/html", data: scheduleTile, status: 200
}


String getGameDate(gameTime) {
    Date gameTimeDateObj = new Date(gameTime)
    def dateFormat = new SimpleDateFormat("MMM d")
    dateFormat.setTimeZone(location.timeZone)        
    def gameDateStr = dateFormat.format(gameTimeDateObj)    
    return gameDateStr
}

String getGameDayOfWeek(gameTime) {
    Date gameTimeDateObj = new Date(gameTime)
    def dateFormat = new SimpleDateFormat("EEE")
    dateFormat.setTimeZone(location.timeZone)        
    def gameDayOfWeekStr = dateFormat.format(gameTimeDateObj)    
    return gameDayOfWeekStr    
}

String getGameTimeOfDay(gameTime) {
    Date gameTimeDateObj = new Date(gameTime)
    def dateFormat = new SimpleDateFormat("h:mm a")
    dateFormat.setTimeZone(location.timeZone)        
    def gameTimeOfDayStr = dateFormat.format(gameTimeDateObj)    
    return gameTimeOfDayStr    
}

def getScheduleData(upcomingSchedule) {
    def scheduleData = []
    for (game in upcomingSchedule) {
        if (game != null) {
            def gameTime = getGameTime(game)              
            def homeTeam = state.teams[game.HomeTeam]
            def awayTeam = state.teams[game.AwayTeam]
            def opponent = null
            def opponentLogo = null
            def homeOrAway = null
            if (homeTeam.key == state.team.key) {
                opponent = awayTeam.name
                opponentLogo = awayTeam.logo
                homeOrAway = "home"
            }
            else if (awayTeam.key == state.team.key) {
                opponent = homeTeam.name
                opponentLogo = homeTeam.logo
                homeOrAway = "away"
            }

            def gameData = [gameTime: gameTime.getTime(), homeOrAway: homeOrAway, opponent: opponent, opponentLogo: opponentLogo]
            scheduleData.add(gameData)
        }        
    }

    scheduleData = scheduleData.sort {it.gameTime}
    def maxNumGames = getNumGamesForScheduleSetting()
    def subListIndex = maxNumGames < scheduleData.size() ? maxNumGames : scheduleData.size()
    scheduleData = scheduleData.subList(0, subListIndex)

    return scheduleData
}

Date getGameTime(game) {
    def dateTime = null
    Date gameTime = null
    if ((game.DateTime == null || game.DateTime == "null") && (game.Day != null && game.Day != "null")) dateTime = game.Day  // game on Day but time not yet set
    else if (game.DateTime != null && game.DateTime != "null") dateTime = game.DateTime
    if (dateTime) gameTime = getDateObj(dateTime)
    return gameTime
}

String getGameTimeStr(Date gameTime) {
    def now = new Date()
    def nextWeek = new Date().clearTime() + 7
    def lastWeek = new Date().clearTime() - 7
    def dateFormat = null
    def gameTimeStrPrefix = ""
    if (gameTime.after(nextWeek)) dateFormat = new SimpleDateFormat("EEE, MMM d h:mm a")
    else if (isToday(gameTime)) {
        gameTimeStrPrefix = "Today "
        dateFormat = new SimpleDateFormat("h:mm a")
    }
    else if (isYesterday(gameTime)) {
        gameTimeStrPrefix = "Yesterday "
        dateFormat = new SimpleDateFormat("h:mm a")
    }
    else if (gameTime.before(lastWeek)) dateFormat = new SimpleDateFormat("EEE, MMM d h:mm a")
    else if (gameTime.before(now)) {
        gameTimeStrPrefix = "This Past "
        dateFormat = new SimpleDateFormat("EEE h:mm a")
    }
    else dateFormat = new SimpleDateFormat("EEE h:mm a")
    dateFormat.setTimeZone(location.timeZone)        
    def gameTimeStr = gameTimeStrPrefix + dateFormat.format(gameTime)    
    return gameTimeStr
}

def updateState(onInitialize = false) {     
    updateAPICallInfo()
    def storedNextGame = state.nextGame
    def storedRecord = getRecord(state.team)
    
    def schedule = fetchTeamSchedule()
    if (schedule == "Error: first byte timeout") {
        log.warn "API call timeout. Not updating state. Will try again later."
        runIn(getUpdateInterval(), update)
        return
    }
    
    def now = new Date()
    def lastGame = null
    def nextGame = null
    def upcomingSchedule = []
    for (game in schedule) {
        def gameTime = getGameTime(game)
        def status = game.Status 
        if (gameTime != null && (gameTime.after(now) || gameTime.equals(now)  || status == "Scheduled" || status == "InProgress"  || status == "Delayed")) {
            // handle upcoming game
            if (nextGame == null) nextGame = game
            else {
                def nextGameTime = getGameTime(nextGame)
                if (status == "InProgress") {
                    if (nextGame.Status != "InProgress") nextGame = game
                    else if (nextGame.Status == "InProgress" && nextGameTime.after(gameTime)) {
                        nextGame = game    // display whichever game started earlier
                    }
                }
                else if (nextGame.Status != "InProgress" && getSecondsBetweenDates(now, gameTime) < getSecondsBetweenDates(now, nextGameTime)) {
                    nextGame = game
                }
            }
            if (gameTime.after(now) || status == "Scheduled") upcomingSchedule.add(game)
        }
        else if (gameTime != null) {
            // handle finished game
            if (lastGame == null) lastGame = game
            else {
                 def lastGameTime = getGameTime(lastGame)
                 if (getSecondsBetweenDates(gameTime, now) < getSecondsBetweenDates(lastGameTime, now)) {
                      lastGame = game
                 }
            }
        }
    }

    state.nextGame = getGameData(nextGame)
    state.lastGame = getGameData(lastGame)
    state.schedule = getScheduleData(upcomingSchedule)
    setStandings()
    
    def hasRecordChanged = hasRecordChanged(storedRecord)
    if (hasRecordChanged) {
        logDebug("Record has changed. Setting last record to ${storedRecord} for determining the result of the last game.")
        state.lastRecord = [wins: storedRecord.wins, losses: storedRecord.losses, asOf: (new Date()).getTime()]  
    }
    else {
        logDebug("Team Record has not changed, eiher because just initialized or because API has not reported a change. No update to state.lastRecord made.")
        if (storedNextGame && state.lastGame?.id == storedNextGame.id) {
            if (state.lastGame.status == "Final" || state.lastGame.status == "F/OT") {
                logDebug("API reported game as over before record was updated.")
            }
            else {
                logDebug("Reason for record not being updated may be that the game was canceled or postponed.")
            }
        }
    }
    if (getHideGameResultSetting() == null || getHideGameResultSetting() == false) {
        def lastGameResult = getLastGameResult(onInitialize)
        if (state.lastGame != null) state.lastGame.status = lastGameResult != null ? lastGameResult : state.lastGame.status
        if (hasRecordChanged && lastGameResult == "Won") handleWinEvent(state.lastGame.opponent.displayName)
        else if (hasRecordChanged && lastGameResult == "Lost") handleLossEvent(state.lastGame.opponent.displayName)
    }

    Date dateToUpdateDisplay = getDateToSwitchFromLastToNextGame()
    if (dateToUpdateDisplay != null && dateToUpdateDisplay.after(now)) runOnce(dateToUpdateDisplay, updateDisplayedGame)
}

def hasRecordChanged(storedRecord) {
    def currentRecord = getRecord(state.team)
    def hasChanged = false
    if (currentRecord.wins != storedRecord.wins || currentRecord.losses != storedRecord.losses) hasChanged = true 
    return hasChanged
}

def getRecord(team) {
    return [wins: team.wins as Integer, losses: team.losses as Integer]
}

def updateRecord(onDemand = false) {
    def update = false
    if (onDemand == true) update = true
    else if (state.updateAttempts != null && state.updateAttempts > 12) {
        // abort update attempt
        state.updateAttempts = 0
    }
    else {
        update = true
        if (state.updateAttempts == null) state.updateAttempts = 1
        else state.updateAttempts++
    }
    if (update == true) {
        def storedRecord = getRecord(state.team)
        setStandings()
        def hasRecordChanged = hasRecordChanged(storedRecord)
        if (hasRecordChanged) state.lastRecord = [wins: storedRecord.wins, losses: storedRecord.losses, asOf: (new Date()).getTime()] 
        else logDebug("Team Record has not changed. No update to state.lastRecord made.")
        def lastGameResult = getLastGameResult(onDemand)
        if (state.lastGame != null) state.lastGame.status = lastGameResult != null ? lastGameResult : state.lastGame.status
        if (hasRecordChanged && lastGameResult == "Won") handleWinEvent(state.lastGame.opponent.displayName)
        else if (hasRecordChanged && lastGameResult == "Lost") handleLossEvent(state.lastGame.opponent.displayName)
        updateDisplayedGame()
    }
}

def getLastGameResult(suppressRetry = false) {
    def result = null
    def recordNotUpdated = false
    def currentRecord = getRecord(state.team)
    if (state.lastRecord == null) {
        def warning = "Unable to determine result of last game for ${state.team.name}. Last team record not stored."
        if (suppressRetry == false) {
            runIn(getUpdateInterval(), updateRecord)
            warning += " Will keep checking."
        }
        logDebug(warning)
        return null
    }
    else if (state.lastGame != null && state.lastRecord.asOf <= state.lastGame.gameTime) {
        logDebug("Record not yet updated for last game.")
        if (suppressRetry == false) {
            runIn(getUpdateInterval(), updateRecord)
            logDebug(" Will keep checking.")
        }
        return null        
    }
    if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1) result = "Lost"
    else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses) result = "Won"
    else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses) recordNotUpdated = true
    if (result == null) {
        def warning = "Warning: Unable to Determine Result of Last Game for ${state.team.name}."
        if (recordNotUpdated == true) {
            warning += " Record has not been updated yet."
            if (suppressRetry == false) {
                runIn(getUpdateInterval(), updateRecord)
                warning += " Will keep checking."
            }
        }
        warning += " Last Record is wins: ${state.lastRecord.wins} losses: ${state.lastRecord.losses}. Current Record is wins: ${currentRecord.wins} losses: ${currentRecord.losses}."
        logDebug(warning)
    }            
    else {
        logDebug("Determined last game result: ${result}")
    }
    return result
}

def handleWinEvent(opponent) {
    pushDeviceButton(3)
    if (isWinEventNotify == true && notificationDevices != null) {          
        notificationDevices.deviceNotification("Victory! ${state.team.displayName} wins over ${opponent}!") 
    }
}

def handleLossEvent(opponent) {
    pushDeviceButton(4)
    if (isLossEventNotify == true && notificationDevices != null) {          
        notificationDevices.deviceNotification("Defeat. ${state.team.displayName} loses to ${opponent}.") 
    }    
}

def updateAPICallInfo() {
    parent.updateAPICallInfo(league)
}

def getGameData(game) {
    def gameData = null
    if (game != null) {
        def gameID = game.GameID
        def gameTime = getGameTime(game)
        def gameTimeStr = getGameTimeStr(gameTime)        
        def status = game.Status
        def channel = game.Channel
        def progress = (status == "InProgress") ? getProgress(game) : null
        
        def homeTeam = state.teams[game.HomeTeam]
        def awayTeam = state.teams[game.AwayTeam]
        def opponent = null
        if (homeTeam.key == state.team.key) opponent = awayTeam
        else if (awayTeam.key == state.team.key) opponent = homeTeam
        else log.error "Team Not Playing in Game"         

        gameData = [id: gameID, gameTime: gameTime.getTime(), gameTimeStr: gameTimeStr, homeTeam: homeTeam, awayTeam: awayTeam, opponent: opponent, status: status, progress: progress, channel: channel]

    }
    return gameData
}

def sendPreGameNotification(myTeam, opponent, timeStr, minsLeft) {
    if (minsLeft == 0) {
        notificationDevices.deviceNotification("Gametime! ${myTeam} play the ${opponent}. ${timeStr}.")           
    }
    else {
        def hours = (minsLeft / 60).intValue()
        def hourStr = ""
        if (hours == 1) hourStr = hours + " hour"
        else if (hours > 1) hourStr = hours + " hours"
        def mins = (minsLeft % 60).intValue()
        def minStr = ""
        if (mins == 1) minStr = mins + " minute"
        else if (mins > 1) minStr = mins + " minutes"
        def timeLeft = (hourStr == "") ? minStr : hourStr + " " + minStr
        notificationDevices.deviceNotification("The ${myTeam} play the ${opponent} in ${timeLeft}. Game starts ${timeStr}.")
    }    
}

def handleFirstPreGameEvent(data) {
    pushDeviceButton(1)
    if (isFirstEventNotify == true && notificationDevices != null) {          
        sendPreGameNotification(state.team.displayName, data.opponent, data.gameTimeStr, getFirstEventAdvanceSetting())
    }
}

def handleSecondPreGameEvent(data) {
    pushDeviceButton(2)
    if (isSecondEventNotify == true && notificationDevices != null) {
        sendPreGameNotification(state.team.displayName, data.opponent, data.gameTimeStr, getSecondEventAdvanceSetting())
    }
}



def scheduleUpdate(Boolean updatingGameInProgress=false) {  
    
    // unschedule pregame events in case the next game has been cancelled since having scheduled those pregame events
    unschedule(handleFirstPreGameEvent)
    unschedule(handleSecondPreGameEvent)
    
    
    def shouldClearTeamState = true
    if (state.nextGame) {
        def nextGameTime = new Date(state.nextGame.gameTime)
        def now = new Date()
        
        if (state.nextGame.status == "Scheduled" && (nextGameTime.after(now) || nextGameTime.equals(now))) {
            if (isToday(nextGameTime)) {
                // if game starts later today, update shortly after gametime
               // only need to schedule update if game is today. If game is tomorrow, update will happen at midnight anyway
                def delayedGameTime = null
                // update game after the 10 minute delay from SportsData.IO
                use(TimeCategory ) {
                    delayedGameTime = nextGameTime + 11.minutes
                }
                runOnce(delayedGameTime, updateGameInProgress)
            }
            
            // Schedule First Pre-Game Event
            def firstEventSecsAdvance = getFirstEventAdvanceSetting()*60*-1
            Date firstEvent = adjustDateBySecs(nextGameTime, firstEventSecsAdvance)
            runOnce(firstEvent, handleFirstPreGameEvent, [data: [opponent: state.nextGame.opponent.displayName, gameTimeStr: state.nextGame.gameTimeStr]])
            
            // Schedule Second Pre-Game Event
            def secondEventSecsAdvance = getSecondEventAdvanceSetting()*60*-1
            Date secondEvent = adjustDateBySecs(nextGameTime, secondEventSecsAdvance)
            runOnce(secondEvent, handleSecondPreGameEvent, [data: [opponent: state.nextGame.opponent.displayName, gameTimeStr: state.nextGame.gameTimeStr]])

        }
        else if (state.nextGame.status == "InProgress") {
            // update in progress game no matter whether game started today or not, since late night game will progress into the next day
            shouldClearTeamState = false // don't clear team state when game is in progress
            runIn(getUpdateInterval(), updateGameInProgress) // while game is in progress, update every 10 minutes
        }
        else if (state.nextGame.status == "Delayed") {
            // update dalyed game no matter whether game started today or not, since late night game will delay into the next day
            shouldClearTeamState = false // don't clear team state when game is in progress
            runIn(1800, updateGameInProgress) // while game is delayed, update every 30 minutes
        }
        else if (state.nextGame.status == "Scheduled" && now.after(nextGameTime)) {
            // game should have already started by now, but sportsdata.io has not updated its API to reflect it yet (10 minute delay for free API). Update in 10 minutes
            logDebug("Game should have started by now, but status still indicates the game is scheduled, not in progress. This is not uncommon. Will check again in 10 minutes.")
            shouldClearTeamState = false // don't clear team state when game is in progress
            runIn(getUpdateInterval(), updateGameInProgress) // update every 10 minutes
        }  
        else if (updatingGameInProgress) {
            // game is over or cancelled. Update game state
            shouldClearTeamState = false // don't clear team state when game is in progress
            update()
        }
    }
    if (shouldClearTeamState && getClearStateSetting()) state.remove("teams") // clear teams state between updates since state is large
}

def doubleDigit(num) {
    def ret = null
    if (num != null) {
        def integer = num as Integer
        ret = String.format("%02d", integer)
    }
    return ret
}

def getProgress(game) {
    def progressStr = ""
    if (game.TimeRemainingMinutes != null && game.TimeRemainingSeconds != null && game.Period != null) {
        def timeRemaining = game.TimeRemainingMinutes + ":" + doubleDigit(game.TimeRemainingSeconds)
        if (league == "Men's College Basketball" || league == "Women's College Basketball") {
             if (game.Period == "1") progressStr = "1st " + timeRemaining
             else if (game.Period == "2") progressStr = "2nd " + timeRemaining
        }
        else if (league == "College Football") {
             if (game.Period == "1" || game.Period == "2" || game.Period == "3" || game.Period == "4") progressStr = game.Period + "Q " + timeRemaining
        
        }
        logDebug("Updating game progress. progressStr = ${progressStr}. game.Period = ${game.Period} with timeRemaining = ${timeRemaining}. Full Game is ${game}")
    }
    else {
        progressStr = "In Progress"
        logDebug("No data for game's progress. Full Game is ${game}")
    }
    return progressStr
}

def updateDisplayedGame() {
    def game = getGameToDisplay()
    def switchValue = getSwitchValue()  
    def tile = getGameTile(game)
    def scheduleTile = getScheduleTile()
    updateDevice([game: game, switchValue: switchValue, tile: tile, scheduleTile: scheduleTile])    
}

def getDateOfNextDayOfWeek(startDate, nextDayOfWeek) {
    Calendar cal = Calendar.getInstance()
    cal.setTimeZone(location.timeZone)
    cal.setTime(startDate)
    
    while (cal.get(Calendar.DAY_OF_WEEK) != nextDayOfWeek) {
        cal.add(Calendar.DAY_OF_WEEK, 1)
    }

    Date next = cal.time
    next.clearTime()
    return next
}


def getNumDaysLaterAtTime(startDate, numDaysLater, atHour, atMinutes) {
    // atTimeHour Integer 0-23, atTimeMinutes Integer 0-59
    Date daysLater = new Date(startDate.getTime())
    daysLater += numDaysLater
    def laterDate = daysLater.copyWith(hourOfDay: atHour, minute: atMinutes, seconds: 0)
    return laterDate
}

Date getDateToSwitchFromLastToNextGame() {
    if (!state.lastGame) return null
    def lastGameTime = new Date(state.lastGame.gameTime)
    def nextGameTime = null
    if (state.nextGame != null) nextGameTime = new Date(state.nextGame.gameTime)
    def now = new Date()
    Date date = null
    if (nextGameTime == null && lastGameTime != null) {
        // if there is no next game, stop displaying the last game at 9AM the morning after the last game
        date = getNumDaysLaterAtTime(lastGameTime, 1, 9, 0)  
    }
    else if (league == "College Football") {
        // switch to display next game on Wednesday
        date = getDateOfNextDayOfWeek(lastGameTime, Calendar.WEDNESDAY)        
    }
    else if (isToday(lastGameTime) && isToday(nextGameTime)) {
        // switch to next game today if next game is today too (double header)
        if (now.after(nextGameTime)) date = now // if double header is already scheduled to start, switch now
        else {
            def switchTime = Math.round(getSecondsBetweenDates(now, nextGameTime) / 120) as Integer // switch halfway between now and the next game time
            Calendar cal = Calendar.getInstance()
            cal.setTimeZone(location.timeZone)
            cal.setTime(lastGameTime)
            cal.add(Calendar.MINUTE, switchTime)
            date = cal.time
        }
    }
    else {
        // switch to display next game at 9AM the morning after the last game
        date = getNumDaysLaterAtTime(lastGameTime, 1, 9, 0)        
    }
    return date
}

def getGameToDisplay() {
    def game = null
    if (state.lastGame == null && state.nextGame != null) game = state.nextGame
    else if (state.nextGame == null && state.lastGame != null) game = state.lastGame
    else if (state.lastGame != null && state.nextGame != null) {
        if (state.nextGame.status == "InProgress" || state.nextGame.status == "Delayed") game = state.nextGame
        else {
            def now = new Date()        
            Date updateAtDate = getDateToSwitchFromLastToNextGame()
            if (updateAtDate != null && now.after(updateAtDate) || now.equals(updateAtDate)) game = state.nextGame
            else game = state.lastGame
        }    
    }
    return game
}

def getUpdatedGameData(gameToUpdate) {
    def schedule = fetchTeamSchedule()
    def updatedGameData = null
    for (game in schedule) {
        def gameID = game.GameID
        if (gameToUpdate.id == gameID) {
            updatedGameData = getGameData(game)
        }
    }
    return updatedGameData
}

def updateGameInProgress() {
    if (state.nextGame) {
        if (!state.teams) setTeams()
        def updatedGameData = getUpdatedGameData(state.nextGame)   
        if (updatedGameData != null) {
            logDebug("Updating game in progress. Progress is ${updatedGameData.progress}. Status is ${updatedGameData.status}")
            state.nextGame.progress = updatedGameData.progress
            state.nextGame.status = updatedGameData.status
        }
        updateDevice([game: state.nextGame, switchValue: getSwitchValue(), tile: getGameTile(state.nextGame), scheduleTile: getScheduleTile()])
        scheduleUpdate(true)
    }
}

def getSwitchValue() {
    def switchValue = "off"
    if (state.lastGame != null && isToday(new Date(state.lastGame.gameTime)) && state.lastGame.status != "Canceled" && state.lastGame.status != "Postponed") switchValue = "on"
    if (state.nextGame != null && isToday(new Date(state.nextGame.gameTime)) && state.nextGame.status != "Canceled" && state.nextGame.status != "Postponed") switchValue = "on"
    if (state.nextGame != null && isYesterday(new Date(state.nextGame.gameTime)) && state.nextGame.status == "InProgress") switchValue = "on" // late night game spilled into the next day
    return switchValue
}

// TO DO: show next game on tile whenever showing last game?
def getGameTile(game) {
    def gameTile = "<div style='overflow:auto;height:90%'></div>"
    def isClearWhenInactiveConfig = getClearWhenInactiveSetting()
    if (!isClearWhenInactiveConfig || (isClearWhenInactiveConfig && !isInactive())) {
        def textColor = getTextColorSetting()
        def fontSize = getFontSizeSetting()
        def colorStyle = ""
        if (textColor != "#000000") colorStyle = "color:" + textColor
        if (game != null) {
            def detailStr = null
            def gameFinished = (game.status == "Scheduled" || game.status == "InProgress") ? false : true
            if (game.status == "InProgress") detailStr = game.progress
            else if (gameFinished) detailStr = game.status
            else detailStr = game.gameTimeStr   
            gameTile = "<div style='overflow:auto;height:90%;font-size:${fontSize}%;${colorStyle}'><table width='100%'>"
            gameTile += "<tr><td width='40%' align=center><img src='${game.awayTeam.logo}' width='100%'></td>"
            gameTile += "<td width='10%' align=center>at</td>"
            gameTile += "<td width='40%' align=center><img src='${game.homeTeam.logo}' width='100%'></td></tr>"
            if (parent.showTeamName) {
                gameTile += "<tr style='padding-bottom: 0em'><td width='40%' align=center>${parent.showTeamRecord && game.awayTeam.rank != null ? game.awayTeam.rank : ''} ${game.awayTeam.name}</td>"
                gameTile += "<td width='10%' align=center></td>"
                gameTile += "<td width='40%' align=center>${parent.showTeamRecord && game.homeTeam.rank != null ? game.homeTeam.rank : ''} ${game.homeTeam.name}</td></tr>" 
            }
            if (parent.showTeamRecord && !getHideGameResultSetting()) {
                gameTile += "<tr><td width='40%' align=center style='font-size:${fontSize*0.75}%;'>${'(' + game.awayTeam.wins + '-' + game.awayTeam.losses + ')'}</td>"
                gameTile += "<td width='10%' align=center></td>"
                gameTile += "<td width='40%' align=center style='font-size:${fontSize*0.75}%;'>${'(' + game.homeTeam.wins + '-' + game.homeTeam.losses + ')'}</td></tr>"  
            }
            gameTile += "<tr style='padding-bottom: 0em'><td width='100%' align=center colspan=3>${detailStr}</td></tr>"
            if (parent.showChannel && game.channel != "null" && game.channel != null && !gameFinished) gameTile += "<tr><td width='100%' align=center colspan=3 style='font-size:${fontSize*0.75}%;'>${game.channel}</td></tr>"
            gameTile += "</table></div>"  
        }
    }
    return gameTile
}

Boolean isInactive() {
    def isInactive = false
    Date now = new Date()
    Date inactiveDateTime = null
    Date activeDateTime = null
    def inactiveThreshold = getInactivityThresholdSetting()
    if (state.lastGame != null && inactiveThreshold != null) {
        def lastGameTime = new Date(state.lastGame.gameTime)
        Calendar cal = Calendar.getInstance()
        cal.setTimeZone(location.timeZone)
        cal.setTime(lastGameTime)
        cal.add(Calendar.HOUR, inactiveThreshold as Integer)
        inactiveDateTime = cal.time
      //  logDebug("Inactivity Post-Game scheduled to start ${inactiveDateTime}")        
    }
    if (state.nextGame != null && inactiveThreshold != null) {
        def nextGameTime = new Date(state.nextGame.gameTime)
        Calendar cal = Calendar.getInstance()
        cal.setTimeZone(location.timeZone)
        cal.setTime(nextGameTime)
        cal.add(Calendar.HOUR, (inactiveThreshold * -1 as Integer))
        activeDateTime = cal.time
      //  logDebug("Inactivity Pre-Game scheduled to stop ${activeDateTime}")
        
    }   
    if (inactiveDateTime != null && activeDateTime != null) {
        if (now.after(inactiveDateTime) && now.before(activeDateTime)) isInactive = true
    }
    else if (inactiveDateTime == null && activeDateTime != null) {
        if (now.before(activeDateTime)) isInactive = true
    }
    else if (inactiveDateTime != null && activeDateTime == null) {
        if (now.after(inactiveDateTime)) isInactive = true
    }
    if (isInactive) logDebug("No game within the past ${inactiveThreshold} hour(s) and within the next ${inactiveThreshold} hour(s). ${getClearWhenInactiveSetting() ? "Hiding tile." : ""}")
    return isInactive
}


def getTeam(teamKey) {
    def returnTeam = null
    state.teams.each { key, tm ->
        if(teamKey == key) {
            returnTeam = tm
        }
    }
    returnTeam
}

def updateDevice(data) {
    parent.updateChildDevice(app.id, data)
}

def pushDeviceButton(buttonNum) {
    parent.pushDeviceButton(app.id, buttonNum)
}

def getConferenceOptions() {
    def conferences = []
    state.teams.each { key, tm ->
        if (tm.conference != null) conferences.add(tm.conference)
    }
    def uniqueConferences = conferences.unique()
    return uniqueConferences
}

def getTeamOptions() {
    def conferenceTeams = []
    state.teams.each { key, tm ->
        if (settings["conference"] == tm.conference) {
            conferenceTeams.add(tm.school + " " + tm.name)
        }
    }
    return conferenceTeams
}

def setMyTeam() {
    state.teams.each { key, tm ->
        def name = tm.school + " " + tm.name
        if(settings["team"] == name) {
            state.team = tm
        }
    }
}

def getMyTeamName() {
    return state.team?.school + " " + state.team?.name
}

def fetchTeams() {
    return sendApiRequest("/scores/json/teams")
}

def fetchTeamSchedule() {
    def schedule = null
    def season = getCurrentSeason()
    if (state.team && season) {
        if (league == "Men's College Basketball" || league == "Women's College Basketball") {
             schedule = sendApiRequest("/scores/json/TeamSchedule/" + season + "/" + getTeamKey())
        }
        else if (league == "College Football") {
            def leagueSchedule = sendApiRequest("/scores/json/Games/" + season)                                                                                                                                                                                                                                                                            
            def teamSchedule = []
            if (leagueSchedule == "Error: first byte timeout") {
                return leagueSchedule
            }
            else if (leagueSchedule != null) {
                for (game in leagueSchedule) {
                    if (game.AwayTeam == state.team.key || game.HomeTeam == state.team.key) {
                        teamSchedule.add(game)
                    }
                }
            }
            schedule = teamSchedule
        }
    }
    return schedule
}

def getCurrentSeason() {
    def season = null
    def seasonDetails = sendApiRequest("/scores/json/CurrentSeason")   
    if (seasonDetails) {
        if (league == "Men's College Basketball" || league == "Women's College Basketball") season = seasonDetails.ApiSeason
        else season = seasonDetails
    }
    else logDebug("No season found.")
    return season
}

def createChild()
{
    def name = state.team?.school
    if (league == "Men's College Basketball") name += " Men's Basketball"
    else if (league == "Women's College Basketball") name += " Women's Basketball"
    else if (league == "College Football") name += " Football"
    
    def lowPriorityThreshold = getLowPriorityThresholdSetting()
    def isLowPriority = getLowPrioritySetting()
    
    parent.createChildDevice(app.id, name, isLowPriority, lowPriorityThreshold)
}

def deleteChild()
{
    parent.deleteChildDevice(app.id)
}

def sendApiRequest(path)
{
    def params = [
		uri: "https://fly.sportsdata.io/",
        path: "v3/" + api[league] + path,
		contentType: "application/json",
		query: [
                key: getAPIKey(),
            ],
		timeout: 1000
	]

    if (body != null)
        params.body = body

    def result = null
    logDebug("Api Call: ${params}")
    parent.countAPICall(league)
    try
    {
        httpGet(params) { resp ->
        result = resp.data
        }                
    }
    catch (Exception e)
    {
        log.warn "sendApiRequest() failed: ${e.message}"
        return null
    }   
    return result
}
            
def getSecondsBetweenDates(Date startDate, Date endDate) {
    try {
        def difference = endDate.getTime() - startDate.getTime()
        return Math.round(difference/1000)
    } catch (ex) {
        log.error "getSecondsBetweenDates Exception: ${ex}"
        return 1000
    }
}

def adjustDateBySecs(Date date, Integer secs) {
    Calendar cal = Calendar.getInstance()
    cal.setTimeZone(location.timeZone)
    cal.setTime(date)
    cal.add(Calendar.SECOND, secs)
    Date newDate = cal.getTime()
    return newDate
}

def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
	}
}
    

def getInterface(type, txt="", link="") {
    switch(type) {
        case "line": 
            return "<hr style='background-color:#555555; height: 1px; border: 0;'></hr>"
            break
        case "header": 
            return "<div style='color:#ffffff;font-weight: bold;background-color:#555555;border: 1px solid;box-shadow: 2px 3px #A9A9A9'> ${txt}</div>"
            break
        case "error": 
            return "<div style='color:#ff0000;font-weight: bold;'>${txt}</div>"
            break
        case "note": 
            return "<div style='color:#333333;font-size: small;'>${txt}</div>"
            break
        case "subField":
            return "<div style='color:#000000;background-color:#ededed;'>${txt}</div>"
            break     
        case "subHeader": 
            return "<div style='color:#000000;font-weight: bold;background-color:#ededed;border: 1px solid;box-shadow: 2px 3px #A9A9A9'> ${txt}</div>"
            break
        case "subSection1Start": 
            return "<div style='color:#000000;background-color:#d4d4d4;border: 0px solid'>"
            break
        case "subSection2Start": 
            return "<div style='color:#000000;background-color:#e0e0e0;border: 0px solid'>"
            break
        case "subSectionEnd":
            return "</div>"
            break
        case "boldText":
            return "<b>${txt}</b>"
            break
        case "link":
            return '<a href="' + link + '" target="_blank" style="color:#51ade5">' + txt + '</a>'
            break
    }
} 

