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

def mainPage() {
    dynamicPage(name: "mainPage") {
       
            section {
              //  header()                
                paragraph getInterface("header", " GameTime College Instance")
                paragraph getInterface("note", "After selecting the league and your team, click DONE. This will create a device for the selected team, listed under the GameTime parent device.")
                input(name:"league", type: "enum", title: "College Sports League", options: leagues, required:true, submitOnChange:true)
                if(!apiKey && league) {
                    input(name:"apiKey", type: "text", title: "SportsData.IO API Key for ${league}", required:true, submitOnChange:true)
                }
                else if (apiKey && league) {
                    if (!state.teams) setTeams()
                    input(name:"conference", type: "enum", title: "Conference", options: getConferenceOptions(), required:true, submitOnChange:true)
                    if (conference) {
                        input(name:"team", type: "enum", title: "Team", options: getTeamOptions(), required:true, submitOnChange:true)
                    }
                }
                
            }
            section (getInterface("header", " Settings")) {
                if (apiKey && league) input(name:"apiKey", type: "text", title: "SportsData.IO API Key for ${league}", required:true, submitOnChange:true)
                if (team) label title: "GameTime Instance Name", defaultValue: team + " GameTime Instance", required:false, submitOnChange:true
                input("clearStateBetweenUpdate", "bool", title: "Clear teams data between updates?", defaultValue: true, required: false)
                getInterface("note", "Enabling Clear 'Teams Data Between Updates' reduces state size. Disabling conserves API calls.")
			    input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false)
		    }
            section("") {
                
                footer()
            }
    }
}

def getClearStateSetting() {
    return clearStateBetweenUpdate != null ? clearStateBetweenUpdate : true
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
    state.clear()
	initialize()
}

def uninstalled() {
    deleteChild()
	logDebug "Uninstalled app"
}

def initialize() {
    if (league && team && apiKey) {
        setTeams()
        setMyTeam()
        createChild()
        update()
        schedule("01 00 00 ? * *", update)
    }
    else log.error "Missing input fields."
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
    def today = new Date().clearTime()
    def dateCopy = new Date(date.getTime())
    def dateObj = dateCopy.clearTime()
    if (dateObj.equals(today)) isToday = true
    return isToday
}

def update() {
    logDebug("Updating GameTime for ${state.team.displayName}")
    if (!state.teams) setTeams()
    updateState()
    updateDisplayedGame()
    scheduleUpdate()
    if (getClearStateSetting()) state.remove("teams") // clear teams state between updates since state is large
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
    def nextWeek = new Date() + 7
    def dateFormat = null
    def gameTimeStrPrefix = ""
    if (gameTime.after(nextWeek)) dateFormat = new SimpleDateFormat("EEE, MMM d h:mm a")
    else if (isToday(gameTime)) {
        gameTimeStrPrefix = "Today "
        dateFormat = new SimpleDateFormat("h:mm a")
    }
    else dateFormat = new SimpleDateFormat("EEE h:mm a")
    dateFormat.setTimeZone(location.timeZone)        
    def gameTimeStr = gameTimeStrPrefix + dateFormat.format(gameTime)    
    return gameTimeStr
}

def updateState() {    
    logDebug("Updating state.")    
    updateAPICallInfo()
    def storedNextGame = state.nextGame
    state.lastRecord = getRecord(state.team)
    
    def schedule = fetchTeamSchedule()
    def now = new Date()
    def lastGame = null
    def nextGame = null
    for (game in schedule) {
        def gameTime = getGameTime(game)
        def status = game.Status 
        if (gameTime.after(now) || gameTime.equals(now)  || status == "Scheduled" || status == "InProgress"  || status == "Delayed") {
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
        }
        else {
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
    logDebug("Updated next game to ${state.nextGame}")
    state.lastGame = getGameData(lastGame)
    logDebug("Updated last game to ${state.lastGame}")
    setStandings()
    
    if (storedNextGame && state.lastGame.id == storedNextGame.id) {
        // the game stored in state as the next game has completed, and is now the last game. Deduce the result of the game.
        logDebug("The game stored in state as the next game has completed, and is now the last game.")
        if (state.lastGame.status == "Final" || state.lastGame.status == "F/OT") {
            // game was played to completion, as opposed to being delayed or canceled. Deduce the winner.
            def lastGameResult = getLastGameResult()
            state.lastGame.status = lastGameResult != null ? lastGameResult : state.lastGame.status
         //   logDebug("The game stored in state as the next game is final. Result was ${state.lastGame.status}")
        }
    }

    Date dateToUpdateDisplay = getDateToSwitchFromLastToNextGame()
    if (dateToUpdateDisplay != null && dateToUpdateDisplay.after(now)) runOnce(dateToUpdateDisplay, updateDisplayedGame)
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
        setStandings()
        def lastGameResult = getLastGameResult(onDemand)
        state.lastGame.status = lastGameResult != null ? lastGameResult : state.lastGame.status
        updateDisplayedGame()
    }
}

def getLastGameResult(onDemand = false) {
    def result = null
    def retryNeeded = false
    def currentRecord = getRecord(state.team)
    if (state.lastRecord == null) {
        log.warn "Unable to determine result of last game for ${state.team.name}. Last team record not stored."
        return null
    }
    if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1) result = "Lost"
    else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses) result = "Won"
    else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses) retryNeeded = true
    if (result == null) {
        def warning = "Warning: Unable to Determine Result of Last Game for ${state.team.name}."
        if (retryNeeded == true) {
            warning += " Record has not been updated yet."
            if (onDemand == false) {
                runIn(600, updateRecord)
                warning += " Will keep checking."
            }
        }
        warning += " Last Record is wins: ${state.lastRecord.wins} losses: ${state.lastRecord.losses}. Current Record is wins: ${currentRecord.wins} losses: ${currentRecord.losses}."
        log.warn warning
    }            
    return result
}

def updateAPICallInfo() {
    Calendar cal = Calendar.getInstance()
    cal.setTimeZone(location.timeZone)
    cal.setTime(new Date())
    def dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    def isMonthStart = dayOfMonth == 1 ? true : false    
    if (isMonthStart) {
        if (state.numMonthsInstalled == null) {
            state.numMonthsInstalled = 0 // don't start average yet since only installed part of the month
            state.apiCallsThisMonth = 0
        }
        else {
            state.numMonthsInstalled++
            if (state.avgAPICallsPerMonth != null) {
                state.avgAPICallsPerMonth = state.avgAPICallsPerMonth + ((state.apiCallsThisMonth - state.avgAPICallsPerMonth) / state.numMonthsInstalled)
            }
            else {
                state.avgAPICallsPerMonth = state.apiCallsThisMonth
            }           
            state.apiCallsThisMonth = 0
        }
    }
}

def getGameData(game) {
    def gameData = null
    if (game != null) {
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

        gameData = [id: game.gameID, gameTime: gameTime.getTime(), gameTimeStr: gameTimeStr, homeTeam: homeTeam, awayTeam: awayTeam, opponent: opponent, status: status, progress: progress, channel: channel]

    }
    return gameData
}

def scheduleUpdate(Boolean updatingGameInProgress=false) {    
    if (state.nextGame) {
        def nextGameTime = new Date(state.nextGame.gameTime)
        def now = new Date()
        
        if (isToday(nextGameTime)) {
            // only need to schedule update if game is today. If game is tomorrow, update will happen at midnight anyway
            if (nextGameTime.after(now) || nextGameTime.equals(now)) {
                // if game starts later today, update shortly after gametime
                def delayedGameTime = null
                // update game after the 10 minute delay from SportsData.IO
                use(TimeCategory ) {
                    delayedGameTime = nextGameTime + 11.minutes
                }
                runOnce(delayedGameTime, updateGameInProgress)
        
            }
            else if (state.nextGame.status == "InProgress") {
                runIn(600, updateGameInProgress) // while game is in progress, update every 10 minutes
            }
            else if (state.nextGame.status == "Delayed") {
                runIn(1800, updateGameInProgress) // while game is delayed, update every 30 minutes
            }
            else if (state.nextGame.status == "Scheduled") {
                // game should have already started by now, but sportsdata.io has not updated its API to reflect it yet (10 minute delay for free API). Update in 10 minutes
                log.warn "Game should have started by now, but status still indicates the game is scheduled, not in progress."
                runIn(600, updateGameInProgress) // update every 10 minutes
            }  
            else if (updatingGameInProgress) {
                // game is over or cancelled. Update game state
                update()
            }
        }
    }
}

def getProgress(game) {
    def progressStr = ""
    if (league == "Men's College Basketball" || league == "Women's College Basketball") {
         if (game.Period == "1") progressStr = "1st " + game.TimeRemainingMinutes + ":" + game.TimeRemainingSeconds
         else if (game.Period == "2") progressStr = "2nd " + game.TimeRemainingMinutes + ":" + game.TimeRemainingSeconds
    }
    else if (league == "College Football") {
         if (game.Period == "1" || game.Period == "2" || game.Period == "3" || game.Period == "4") progressStr = game.Period + "Q " + game.TimeRemainingMinutes + ":" + game.TimeRemainingSeconds
    }
    return progressStr
}

def updateDisplayedGame() {
    def game = getGameToDisplay()
    def switchValue = getSwitchValue()  
    def tile = getGameTile(game)
    updateDevice([game: game, switchValue: switchValue, tile: tile])    
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
    if (!state.lastGame || !state.nextGame) return null
    def lastGameTime = new Date(state.lastGame.gameTime)
    def nextGameTime = new Date(state.nextGame.gameTime)
    def now = new Date()
    Date date = null
    if (league == "College Football") {
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
        def now = new Date()        
        Date updateAtDate = getDateToSwitchFromLastToNextGame()
        if (now.after(updateAtDate) || now.equals(updateAtDate)) game = state.nextGame
        else game = state.lastGame
        
    }
    return game
}

def getUpdatedGameData(gameToUpdate) {
    def schedule = fetchTeamSchedule()
    def updatedGameData = null
    for (game in schedule) {
        def gameID = getGameID(game)
        if (gameToUpdate.id == gameID) {
            updatedGameData = getGameData(game)
        }
    }
    return updatedGameData
}

def updateGameInProgress() {
    if (state.nextGame) {
        def updatedGameData = getUpdatedGameData(state.nextGame)   
        logDebug("Updating game in progress. Progress is ${updatedGameData.progress}. Status is ${updatedGameData.status}")
        state.nextGame.progress = updatedGameData.progress
        state.nextGame.status = updatedGameData.status
        updateDevice([game: state.nextGame, switchValue: getSwitchValue(), tile: getGameTile(state.nextGame)])
        scheduleUpdate(true)
    }
}

def getSwitchValue() {
    def switchValue = "off"
    if (state.lastGame && isToday(new Date(state.lastGame.gameTime))) switchValue = "on"
    if (state.nextGame && isToday(new Date(state.nextGame.gameTime))) switchValue = "on"
    return switchValue
}

// TO DO: show next game on tile whenever showing last game?
def getGameTile(game) {
    def gameTile = null
    def textColor = parent.getTextColor()
    def colorStyle = ""
    if (textColor != "#000000") colorStyle = "color:" + textColor
    if (game != null) {
        def detailStr = null
        def gameFinished = (game.status == "Final" || game.status == "F/OT" || game.status == "Won" || game.status == "Lost") ? true : false
        if (game.status == "InProgress") detailStr = game.progress
        else if (gameFinished) detailStr = game.status
        else detailStr = game.gameTimeStr   
        gameTile = "<div style='overflow:auto;height:90%;${colorStyle}'><table width='100%'>"
        gameTile += "<tr><td width='40%' align=center><img src='${game.awayTeam.logo}' width='100%'></td>"
        gameTile += "<td width='10%' align=center>at</td>"
        gameTile += "<td width='40%' align=center><img src='${game.homeTeam.logo}' width='100%'></td></tr>"
        if (parent.showTeamName) {
            gameTile += "<tr style='padding-bottom: 0em'><td width='40%' align=center>${parent.showTeamRecord && awayTeam.rank != null ? awayTeam.rank : ''} ${awayTeam.name}</td>"
            gameTile += "<td width='10%' align=center></td>"
            gameTile += "<td width='40%' align=center>${parent.showTeamRecord && homeTeam.rank != null ? homeTeam.rank : ''} ${homeTeam.name}</td></tr>" 
        }
        if (parent.showTeamRecord) {
            gameTile += "<tr><td width='40%' align=center style='font-size:75%'>${'(' + game.awayTeam.wins + '-' + game.awayTeam.losses + ')'}</td>"
            gameTile += "<td width='10%' align=center></td>"
            gameTile += "<td width='40%' align=center style='font-size:75%'>${'(' + game.homeTeam.wins + '-' + game.homeTeam.losses + ')'}</td></tr>"  
        }
        gameTile += "<tr style='padding-bottom: 0em'><td width='100%' align=center colspan=3>${detailStr}</td></tr>"
        if (parent.showChannel && game.channel != "null" && game.channel != null && !gameFinished) gameTile += "<tr><td width='100%' align=center colspan=3 style='font-size:75%'>${game.channel}</td></tr>"
        gameTile += "</table></div>"  
    }
    else gameTile = "<div style='overflow:auto;height:90%'></div>"
    // If no game to display, display nothing (keep dashboard clean)
    return gameTile
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
            if (leagueSchedule) {
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
    parent.createChildDevice(app.id, name)
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
                key: apiKey,
            ],
		timeout: 300
	]

    if (body != null)
        params.body = body

    logDebug("API Call: ${params}")
    def result = null
    httpGet(params) { resp ->
        result = resp.data
    }
    if (state.apiCallsThisMonth != null) state.apiCallsThisMonth++
    else state.apiCallsThisMonth = 1
    if (state.apiCallsThisMonth > 1000) log.warn "API Call Limit of 1000 per month exceeded. Uncheck 'Clear Teams Data Between Updates' in the app to reduce the number of API calls."
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

