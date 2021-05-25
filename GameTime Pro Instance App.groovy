/**
 *  GameTime Professional Instance
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
 *  v1.0.0 - Full Feature Beta
 */
import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.time.TimeCategory

definition(
    name: "GameTime Professional Instance",
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

@Field leagues = ["MLB", "NBA", "NFL", "NHL"]
@Field api = ["NBA":"nba", "MLB":"mlb", "NFL":"nfl", "NHL":"nhl"]

def mainPage() {
    dynamicPage(name: "mainPage") {
       
            section {
              //  header()                
                paragraph getInterface("header", " GameTime Professional Instance")
                paragraph getInterface("note", "After selecting the league and your team, click DONE. This will create a device for the selected team, listed under the GameTime parent device.")
                input(name:"league", type: "enum", title: "Professional Sports League", options: leagues, required:true, submitOnChange:true)
                if(!apiKey && league) {
                    input(name:"apiKey", type: "text", title: "SportsData.IO API Key for ${league}", required:true, submitOnChange:true)
                }
                else if (apiKey && league) {
                    if (!state.season) setSeason()
                    if (!state.teams) setTeams()
                    input(name:"team", type: "enum", title: "Team", options: getTeamOptions(), required:true, submitOnChange:true)
                }
                
            }
            section (getInterface("header", " Settings")) {
                if (apiKey && league) input(name:"apiKey", type: "text", title: "SportsData.IO API Key for ${league}", required:true, submitOnChange:true)
                if (team) label title: "GameTime Instance Name", required:false, submitOnChange:true
			    input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false)
		    }
            section("") {
                
                footer()
            }
    }
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
        setSeason()
        setTeams()
        setStandings()
        setMyTeam()
        createChild()        
        update()
        schedule("01 00 00 ? * *", setSeason)
        schedule("15 00 00 ? * *", update)
    }
    else log.error "Missing input fields."
}


def isToday(Date date) {
    def isToday = false
    def today = new Date().clearTime()
    def dateCopy = new Date(date.getTime())
    def dateObj = dateCopy.clearTime()
    if (dateObj.equals(today)) isToday = true
    return isToday
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
    
    if (storedNextGame && state.lastGame?.id == storedNextGame.id) {
        // the game stored in state as the next game has completed, and is now the last game. Deduce the result of the game.
        logDebug("The game stored in state as the next game has completed, and is now the last game.")
        if (state.lastGame.status == "Final" || state.lastGame.status == "F/OT" || state.lastGame.status == "F/SO") {
            // game was played to completion, as opposed to being delayed or canceled. Deduce the winner.
            def lastGameResult = getLastGameResult()
            state.lastGame.status = lastGameResult != null ? lastGameResult : state.lastGame.status
          //  logDebug("The game stored in state as the next game is final. Result was ${state.lastGame.status}")
        }
    }
    
    Date dateToUpdateDisplay = getDateToSwitchFromLastToNextGame()
    if (dateToUpdateDisplay != null && dateToUpdateDisplay.after(now)) runOnce(dateToUpdateDisplay, updateDisplayedGame)

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

def getRecord(team) {
    return [wins: team.wins, losses: team.losses, overtimeLosses: team.overtimeLosses, ties: team.ties]
}

def getLastGameResult(onDemand = false) {
    def result = null
    def retryNeeded = false
    def currentRecord = getRecord(state.team)
    if (state.lastRecord == null) {
        log.warn "Unable to determine result of last game for ${state.team.displayName}. Last team record not stored."
        return null
    }
    if (league == "NHL") {
        if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1 && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses) result = "Lost"
        else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses) result = "Won"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses + 1) result = "Lost in OT"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses) retryNeeded = true
        
    }
    else if (league == "NFL") {
        if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1 && currentRecord.ties == state.lastRecord.ties) result = "Lost"
        else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses && currentRecord.ties == state.lastRecord.ties) result = "Won"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.ties == state.lastRecord.ties + 1) result = "Tied"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.ties == state.lastRecord.ties) retryNeeded = true
     }
    else {
        if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1) result = "Lost"
        else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses) result = "Won"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses) retryNeeded = true
    }
    if (result == null) {
        def warning = "Warning: Unable to Determine Result of Last Game for ${state.team.displayName}."
        if (retryNeeded == true) {
            warning += " Record has not been updated yet."
            if (onDemand == false) {
                runIn(600, updateRecord)
                warning += " Will keep checking."
            }
        }
        warning += " Last Record is wins: ${state.lastRecord.wins} losses: ${state.lastRecord.losses}${league == "NFL" ? " ties: " + state.lastRecord.ties : ""}${league == "NHL" ? " OT losses: " + state.lastRecord.overtimeLosses : ""}. Current Record is wins: ${currentRecord.wins} losses: ${currentRecord.losses}${league == "NFL" ? " ties: " + currentRecord.ties : ""}${league == "NHL" ? " OT losses: " + currentRecord.overtimeLosses : ""}."
        log.warn warning
    }            
    return result
}

def getGameData(game) {
    def gameData = null
    if (game != null) {
        def gameID = getGameID(game)
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

def getProgress(game) {
    def progressStr = ""
    if (league == "NFL") {
        def gameData = fetchScore(game.ScoreID)
        def quarter = gameData.quarter
        def timeRemaining = gameData.timeRemaining
        if (timeRemaining != null && timeRemaining != "NULL") {
            if (quarter == "1" || quarter == "2" || quarter == "3" || quarter == "4") {
                progressStr = quarter + "Q " + timeRemaining
            }
            else if (quarter == "Half") progressStr = quarter
            else if (quarter == "OT") progressStr = quarter + " " + timeRemaining
        }
        else if (timeRemaining == null || timeRemaining == "NULL") {
            if (quarter == "1") progressStr = "End 1st"
            else if (quarter == "2") progressStr = "Half"
            else if (quarter == "Half") progressStr = "Half"
            else if (quarter == "3") progressStr = "End 3rd"
            else if (quarter == "4") progressStr = "End 4th"
            else if (quarter == "OT") progressStr = "End OT"
        }
        else log.warn "Unexpected progress string"
    }
    else if (league == "MLB") {
        if (game.InningHalf == "T") progressStr = "Top "
        else if (game.InningHalf == "B") progressStr = "Bot "
        def inningNum = game.Inning as Integer
        progressStr += game.Inning + getOrdinal(inningNum)
    }
    else if (league == "NBA") {
        def quarter = game.Quarter
        if (game.TimeRemainingMinutes != null && game.TimeRemainingSeconds != null) {
            if (quarter == "1" || quarter == "2" || quarter == "3" || quarter == "4") {
                 progressStr = quarter + "Q " + game.TimeRemainingMinutes + ":" + game.TimeRemainingSeconds
            }
            else if (quarter == "Half") progressStr = quarter
            else if (quarter == "OT") progressStr = quarter + " " + game.TimeRemainingMinutes + ":" + game.TimeRemainingSeconds
        }
        else if (game.TimeRemainingMinutes == null && game.TimeRemainingSeconds == null) {
            if (quarter == "1") progressStr = "End 1st"
            else if (quarter == "2") progressStr = "Half"
            else if (quarter == "Half") progressStr = "Half"
            else if (quarter == "3") progressStr = "End 3rd"
            else if (quarter == "4") progressStr = "End 4th"
            else if (quarter == "OT") progressStr = "End OT"
        }
        else log.warn "Unexpected progress string"
        
    }
    else if (league == "NHL") {
        def timeRemaining = game.TimeRemainingMinutes + ":" + game.TimeRemainingSeconds
        def period = game.Period
        if (game.TimeRemainingMinutes != null && game.TimeRemainingSeconds != null) {
            if (period == "1") progressStr = "1st " + timeRemaining
            else if (period == "2") progressStr = "2nd " + timeRemaining
            else if (period == "3") progressStr = "3rd " + timeRemaining
            else if (period == "SO") progressStr = period
            else if (period == "OT") progressStr = period + " " + timeRemaining
        }
        else if (game.TimeRemainingMinutes == null && game.TimeRemainingSeconds == null) {
            if (period == "1") progressStr = "End 1st"
            else if (period == "2") progressStr = "End 2nd"
            else if (period == "3") progressStr = "End 3rd"
            else if (period == "SO") progressStr = period
            else if (period == "OT") progressStr = "End OT"
        }
        else log.warn "Unexpected progress string"
   }    
    return progressStr
}

def getGameID(game) {
    def id = null
    if (league == "NFL") id = game.GameKey
    else id = game.GameID
    return id
}

def update() {
    logDebug("Updating GameTime for ${state.team.displayName}")
    updateState()
    updateDisplayedGame()
    scheduleUpdate()
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
    if (league == "NFL") {
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
        def gameFinished = (game.status == "Final" || game.status == "F/OT" || game.status == "F/SO" || game.status == "Won" || game.status == "Lost" || game.status == "Lost in OT" || game.status == "Tied") ? true : false
        if (game.status == "InProgress") detailStr = game.progress
        else if (gameFinished) detailStr = game.status
        else detailStr = game.gameTimeStr   
        
        gameTile = "<div style='overflow:auto;height:90%;${colorStyle};'><table width='100%'>"
        gameTile += "<tr><td width='40%' align=center><img src='${game.awayTeam.logo}' width='100%'></td>"
        gameTile += "<td width='10%' align=center>at</td>"
        gameTile += "<td width='40%' align=center><img src='${game.homeTeam.logo}' width='100%'></td></tr>"
        if (parent.showTeamName) {
            gameTile += "<tr style='padding-bottom: 0em'><td width='40%' align=center>${game.awayTeam.name}</td>"
            gameTile += "<td width='10%' align=center></td>"
            gameTile += "<td width='40%' align=center>${game.homeTeam.name}</td></tr>" 
        }
        if (parent.showTeamRecord) {
            def awayTeamRecordSuffix = ""
            if (league == "NHL") awayTeamRecordSuffix = "-" + game.awayTeam.overtimeLosses
            else if (league == "NFL") awayTeamRecordSuffix = "-" + game.awayTeam.ties
            def homeTeamRecordSuffix = ""
            if (league == "NHL") homeTeamRecordSuffix = "-" + game.homeTeam.overtimeLosses
            else if (league == "NFL") homeTeamRecordSuffix = "-" + game.homeTeam.ties    
            gameTile += "<tr><td width='40%' align=center style='font-size:75%'>${'(' + game.awayTeam.wins + '-' + game.awayTeam.losses + awayTeamRecordSuffix + ')'}</td>"
            gameTile += "<td width='10%' align=center></td>"
            gameTile += "<td width='40%' align=center style='font-size:75%'>${'(' + game.homeTeam.wins + '-' + game.homeTeam.losses + homeTeamRecordSuffix + ')'}</td></tr>"  
        }
        gameTile += "<tr style='padding-bottom: 0em; font-size: 100%;'><td width='100%' align=center colspan=3>${detailStr}</td></tr>"
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

def getTeamOptions() {
    def teamOptions = []
    state.teams.each { key, tm ->
        teamOptions.add(tm.displayName)
    }
    return teamOptions
}

def setMyTeam() {
    state.teams.each { key, tm ->
        if(settings["team"] == tm.displayName) {
            state.team = tm
        }
    }
}

def fetchTeamSchedule() {
    if (!state.team) {
        log.error "No Schedule fetched. Team State Not Set."
        return null
    }
    def leagueSchedule = null
    if (state.season) {
        if (league == "NFL") leagueSchedule = sendApiRequest("/scores/json/Schedules/" + state.season)
        else leagueSchedule = sendApiRequest("/scores/json/Games/" + state.season)
    }
    def teamSchedule = []
    if (leagueSchedule) {
        for (game in leagueSchedule) {
            if (game.AwayTeam == state.team.key || game.HomeTeam == state.team.key) {
             //   logDebug("Adding game to team schedule with gametime of ${game.DateTime}")
                teamSchedule.add(game)
            }
        }
    }
    else log.warn "No upcoming schedule for ${team} in ${league}"
    if (teamSchedule == []) log.warn "No upcoming schedule for ${team} in ${league}"
    return teamSchedule
}

def fetchScore(id) {
    def data = null
    if (league == "NFL") {
        def boxScore = sendApiRequest("/stats/json/BoxScoreByScoreIDV3/" + id)
        data = [quarter: boxScore.Score.Quarter, timeRemaining:boxScore.Score.TimeRemaining]
    }
    return data
}

def fetchStandings() {
    def standings = null
    if (state.season) {
         standings = sendApiRequest("/scores/json/Standings/" + state.season)
    }
    return standings    
}

def setTeams() {
   if (!state.teams) state.teams = [:]
   def fullTeams = fetchTeams()
   for (tm in fullTeams) {
       def displayName = ""
       if (league == "NFL") displayName = tm.FullName
       else displayName = tm.City + " " + tm.Name
       def teamMap = [id: tm.TeamID, key: tm.Key, city: tm.City, name: tm.Name, logo: tm.WikipediaLogoUrl, displayName: displayName, wins: null, losses: null, overtimeLosses: null, ties: null]
       state.teams[tm.Key] = teamMap
   }
}

def setStandings() {
   def standings = fetchStandings()
   for (standing in standings) {
       def key = null
       if (league == "NFL") key = standing.Team
       else key = standing.Key
       state.teams[key]?.wins = getIntValue(standing.Wins)
       state.teams[key]?.losses = getIntValue(standing.Losses)
       if (league == "NFL") state.teams[key]?.ties = getIntValue(standing.Ties) 
       if (league == "NHL") state.teams[key]?.overtimeLosses = getIntValue(standing.OvertimeLosses)
   } 
   setMyTeam()
}

def getIntValue(standingComponent) {
    def value = 0
    if (standingComponent != null && standingComponent != "null" && standingComponent != "NULL") {
        value = standingComponent as Integer
    }
    return value
}

def fetchTeams() {
    def tms = sendApiRequest("/scores/json/teams")   
    if (!tms) log.error("No Teams found.")
    return tms
}

def setSeason() {
    def season = sendApiRequest("/scores/json/CurrentSeason")   
    if (!season) log.error("No season found.")
    if (league == "NFL") state.season = season
    else state.season = season.ApiSeason
}

def getMyTeamName() {
    return state.team?.displayName
}

def createChild()
{
    def name = state.team?.displayName
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

    def result = null
    logDebug("Api Call: ${params}")
    httpGet(params) { resp ->
        result = resp.data
    }
    if (state.apiCallsThisMonth != null) state.apiCallsThisMonth++
    else state.apiCallsThisMonth = 1
    if (state.apiCallsThisMonth > 1000) log.warn "API Call Limit of 1000 per month exceeded. Uncheck 'Clear Teams Data Between Updates' in the app to reduce the number of API calls."
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
            
def getSecondsBetweenDates(Date startDate, Date endDate) {
    try {
        def difference = endDate.getTime() - startDate.getTime()
        return Math.round(difference/1000)
    } catch (ex) {
        log.error "getSecondsBetweenDates Exception: ${ex}"
        return 1000
    }
}

def getOrdinal(num) {
    // get ordinal number for num range 1-30
    def ord = null
    if (num == 1 || num == 21) ord = "st"
    else if (num == 2 || num == 22) ord = "nd"
    else if (num == 3 || num == 23) ord = "rd"
    else ord = "th"
    return ord
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

