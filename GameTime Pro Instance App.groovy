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
 *  v1.2.0 - Full Feature Beta
 *  v1.2.1 - Bug fixes
 *  v1.2.2 - Update scheduling if late night game; Time Formatting improvements
 *  v1.2.3 - Bug fixes
 *  v1.2.4 - Added option to hide game result spoilers
 *  v1.2.5 - Bug fixes
 *  v1.2.6 - Bug fixes
 *  v1.2.7 - Hide record when hide game spoilers
 *  v1.3.0 - Added option to designate team as Low Priority
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
                if (team) input name: "hideGameResult", title:"Hide Game Result?", type:"bool", required:false, submitOnChange:false
                if (team) {
                    input name: "lowPriority", title:"Low Priority Team?", type:"bool", required:false, submitOnChange:false
                    input name: "priorityHourThreshold", type: "number", title: "Low Priority Team Hour Threshold", defaultValue: 24
                    paragraph getInterface("note", "A low priority team will only display on the 'all teams' GameTime device if no higher priority team has a game within X hours. The Low Priority Team Hour Threshold specifies X.") 
                }
                if (team) label title: "GameTime Instance Name", required:false, submitOnChange:true
                if (apiKey && league) input(name:"apiKey", type: "text", title: "SportsData.IO API Key for ${league}", required:true, submitOnChange:true)
			    input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false)
		    }
            section("") {
                
                footer()
            }
    }
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

def getTextColorSetting() {
    return parent.getTextColorSetting()
}

def getTeamKey() {
    return state.team?.key  
}

def getInactivityThresholdSetting() {
    return parent.getInactivityThresholdSetting()
}

def getClearWhenInactiveSetting() {    
  //  logDebug("In getClearWhenInactive() in child")
    return parent.getClearWhenInactiveSetting()
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
    if (league && team && apiKey) {
        setSeason()
        setTeams()
        setStandings()
        setMyTeam()
        createChild()        
        update(true)
        schedule("01 00 00 ? * *", setSeason)
        schedule("15 00 00 ? * *", scheduledUpdate)
    }
    else log.error "Missing input fields."
}

def scheduledUpdate()
{
    update(true)    
}


def isToday(Date date) {
    def isToday = false
    def today = new Date().clearTime()
    def dateCopy = new Date(date.getTime())
    def dateObj = dateCopy.clearTime()
    if (dateObj.equals(today)) isToday = true
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

def updateState(onInitialize = false) {
    updateAPICallInfo()
    def storedNextGame = state.nextGame
    def storedRecord = getRecord(state.team)
    
    def schedule = fetchTeamSchedule()
    if (schedule == "Error: first byte timeout") {
        log.warn "API call timeout. Not updating state. Will try again later."
        runIn(600, update)
        return
    }
    
    def now = new Date()
    def lastGame = null
    def nextGame = null
    for (game in schedule) {
        def gameTime = getGameTime(game)
        def status = game.Status 
        if (gameTime.after(now) || gameTime.equals(now)  || status == "Scheduled" || status == "InProgress"  || status == "Delayed") {
            // handle upcoming game
            // TO DO: Consider a game with a status of "Canceled" as the next game or not?
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
    state.lastGame = getGameData(lastGame)
    setStandings()
    
    if (hasRecordChanged(storedRecord)) {
        logDebug("Team Record has changed. Setting last record to ${storedRecord} for determining the result of the last game.")
        state.lastRecord = [wins: storedRecord.wins, losses: storedRecord.losses, overtimeLosses: storedRecord.overtimeLosses, ties: storedRecord.ties, asOf: now.getTime()]   
    }
    else {
        logDebug("Team Record has not changed, eiher because just initialized or because API has not reported a change. No update to state.lastRecord made.")
        if (storedNextGame != null && state.lastGame?.id == storedNextGame.id) {
            if (state.lastGame.status == "Final" || state.lastGame.status == "F/OT" || state.lastGame.status == "F/SO") {
                logDebug("API reported game as over before record was updated.")
            }
            else {
                logDebug("Reason for record not being updated may be that the game was canceled or postponed.")
            }
        }
    }
    if (getHideGameResultSetting() == null || getHideGameResultSetting() == false) {
        def lastGameResult = getLastGameResult(onInitialize)
        // TO DO: build in a safeguard to make sure status is only set to result if the last game's status was F, F/OT, or F/SO (not canceled, etc.)
        if (state.lastGame != null) state.lastGame.status = lastGameResult != null ? lastGameResult : state.lastGame.status
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
        def storedRecord = getRecord(state.team)
        setStandings()
        if (hasRecordChanged(storedRecord)) {
            state.lastRecord = [wins: storedRecord.wins, losses: storedRecord.losses, overtimeLosses: storedRecord.overtimeLosses, ties: storedRecord.ties, asOf: (new Date()).getTime()]  
        }
        else logDebug("Team Record has not changed. No update to state.lastRecord made.")
        def lastGameResult = getLastGameResult(onDemand)
        if (state.lastGame != null) state.lastGame.status = lastGameResult != null ? lastGameResult : state.lastGame.status
        updateDisplayedGame()
    }
}

def getRecord(team) {
    return [wins: team.wins, losses: team.losses, overtimeLosses: team.overtimeLosses, ties: team.ties]
}

def hasRecordChanged(storedRecord) {
    def currentRecord = getRecord(state.team)
    def hasChanged = false
    if (league == "NHL") {
        if (currentRecord.wins != storedRecord.wins || currentRecord.losses != storedRecord.losses || currentRecord.overtimeLosses != storedRecord.overtimeLosses) hasChanged = true        
    }
    else if (league == "NFL") {
        if (currentRecord.wins != storedRecord.wins || currentRecord.losses != storedRecord.losses || currentRecord.ties != storedRecord.ties) hasChanged = true
     }
    else {
        if (currentRecord.wins != storedRecord.wins || currentRecord.losses != storedRecord.losses) hasChanged = true
    }  
    logDebug("Returning ${hasChanged} from hasRecordChanged() with currentRecord.wins=${currentRecord.wins} storedRecord.wins=${storedRecord.wins} currentRecord.losses=${currentRecord.losses} storedRecord.losses=${storedRecord.losses}  currentRecord.overtimeLosses=${currentRecord.overtimeLosses} storedRecord.overtimeLosses=${storedRecord.overtimeLosses}  currentRecord.ties=${currentRecord.ties} storedRecord.ties=${storedRecord.ties}")
    return hasChanged
}

def getLastGameResult(suppressRetry = false) {
    def result = null
    def recordNotUpdated = false
    def currentRecord = getRecord(state.team)
    if (state.lastRecord == null) {
        def warning = "Unable to determine result of last game for ${state.team.displayName}. Last team record not stored."
        if (suppressRetry == false) {
            runIn(600, updateRecord)
            warning += " Will keep checking."
        }
        logDebug(warning)
        return null
    }
    else if (state.lastGame != null && state.lastRecord.asOf <= state.lastGame.gameTime) {
        logDebug("Record not yet updated for last game.")
        if (suppressRetry == false) {
            runIn(600, updateRecord)
            logDebug(" Will keep checking.")
        }
        return null        
    }
    if (league == "NHL") {
        if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1 && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses) result = "Lost"
        else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses) result = "Won"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses + 1) result = "Lost in OT"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.overtimeLosses == state.lastRecord.overtimeLosses) recordNotUpdated = true
        
    }
    else if (league == "NFL") {
        if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1 && currentRecord.ties == state.lastRecord.ties) result = "Lost"
        else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses && currentRecord.ties == state.lastRecord.ties) result = "Won"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.ties == state.lastRecord.ties + 1) result = "Tied"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses && currentRecord.ties == state.lastRecord.ties) recordNotUpdated = true
     }
    else {
        if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses + 1) result = "Lost"
        else if (currentRecord.wins == state.lastRecord.wins + 1 && currentRecord.losses == state.lastRecord.losses) result = "Won"
        else if (currentRecord.wins == state.lastRecord.wins && currentRecord.losses == state.lastRecord.losses) recordNotUpdated = true
    }
    if (result == null) {
        def warning = "Warning: Unable to Determine Result of Last Game for ${state.team.displayName}."
        if (recordNotUpdated == true) {
            warning += " Record has not been updated yet."
            if (suppressRetry == false) {
                runIn(600, updateRecord)
                warning += " Will keep checking."
            }
        }
        warning += " Last Record is wins: ${state.lastRecord.wins} losses: ${state.lastRecord.losses}${league == "NFL" ? " ties: " + state.lastRecord.ties : ""}${league == "NHL" ? " OT losses: " + state.lastRecord.overtimeLosses : ""}. Current Record is wins: ${currentRecord.wins} losses: ${currentRecord.losses}${league == "NFL" ? " ties: " + currentRecord.ties : ""}${league == "NHL" ? " OT losses: " + currentRecord.overtimeLosses : ""}."
        logDebug(warning)
    }          
    else logDebug("Determined last game result: ${result}")
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
        def timeRemaining = game.TimeRemainingMinutes + ":" + doubleDigit(game.TimeRemainingSeconds)
        if (game.TimeRemainingMinutes != null && game.TimeRemainingSeconds != null) {
            if (quarter == "1" || quarter == "2" || quarter == "3" || quarter == "4") {
                 progressStr = quarter + "Q " + timeRemaining
            }
            else if (quarter == "Half") progressStr = quarter
            else if (quarter == "OT") progressStr = quarter + " " + timeRemaining
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
        def timeRemaining = game.TimeRemainingMinutes + ":" + doubleDigit(game.TimeRemainingSeconds)
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

def update(onInitialize = false) {
    logDebug("Updating GameTime for ${state.team.displayName}")
    updateState(onInitialize)
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
        if (state.nextGame.status == "InProgress" || state.nextGame.status == "Delayed") game = state.nextGame
        else {
            def now = new Date()        
            Date updateAtDate = getDateToSwitchFromLastToNextGame()
            if (now.after(updateAtDate) || now.equals(updateAtDate)) game = state.nextGame
            else game = state.lastGame
        }        
    }
    return game
}

def scheduleUpdate(Boolean updatingGameInProgress=false) {    
    if (state.nextGame) {
        def nextGameTime = new Date(state.nextGame.gameTime)
        def now = new Date()
        
        if (state.nextGame.status == "Scheduled" && (nextGameTime.after(now) || nextGameTime.equals(now)) && isToday(nextGameTime)) {
            // if game starts later today, update shortly after gametime
            // only need to schedule update if game is today. If game is tomorrow, update will happen at midnight anyway
            def delayedGameTime = null
            // update game after the 10 minute delay from SportsData.IO
            use(TimeCategory ) {
                delayedGameTime = nextGameTime + 11.minutes
            }
            runOnce(delayedGameTime, updateGameInProgress)
        
        }
        else if (state.nextGame.status == "InProgress") {
            // update in progress game no matter whether game started today or not, since late night game will progress into the next day
            runIn(600, updateGameInProgress) // while game is in progress, update every 10 minutes
        }
        else if (state.nextGame.status == "Delayed") {
            // update dalyed game no matter whether game started today or not, since late night game will delay into the next day
            runIn(1800, updateGameInProgress) // while game is delayed, update every 30 minutes
        }
        else if (state.nextGame.status == "Scheduled" && now.after(nextGameTime)) {
            // game should have already started by now, but sportsdata.io has not updated its API to reflect it yet (10 minute delay for free API). Update in 10 minutes
            logDebug("Game should have started by now, but status still indicates the game is scheduled, not in progress. This is not uncommon. Will check again in 10 minutes.")
            runIn(600, updateGameInProgress) // update every 10 minutes
        }  
        else if (updatingGameInProgress) {
            // game is over or cancelled. Update game state
            update()
        }
    }
}

def updateGameInProgress() {
    if (state.nextGame) {
        def updatedGameData = getUpdatedGameData(state.nextGame)   
        if (updatedGameData != null) {
            logDebug("Updating game in progress. Progress is ${updatedGameData.progress}. Status is ${updatedGameData.status}")
            state.nextGame.progress = updatedGameData.progress
            state.nextGame.status = updatedGameData.status
        }
        updateDevice([game: state.nextGame, switchValue: getSwitchValue(), tile: getGameTile(state.nextGame)])
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
    logDebug("Getting game tile for ${state.team.displayName}")
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
        
            gameTile = "<div style='overflow:auto;height:90%;font-size:${fontSize}%;${colorStyle};'><table width='100%'>"
            gameTile += "<tr><td width='40%' align=center><img src='${game.awayTeam.logo}' width='100%'></td>"
            gameTile += "<td width='10%' align=center>at</td>"
            gameTile += "<td width='40%' align=center><img src='${game.homeTeam.logo}' width='100%'></td></tr>"
            if (parent.showTeamName) {
                gameTile += "<tr style='padding-bottom: 0em'><td width='40%' align=center>${game.awayTeam.name}</td>"
                gameTile += "<td width='10%' align=center></td>"
                gameTile += "<td width='40%' align=center>${game.homeTeam.name}</td></tr>" 
            }
            if (parent.showTeamRecord && !getHideGameResultSetting()) {
                def awayTeamRecordSuffix = ""
                if (league == "NHL") awayTeamRecordSuffix = "-" + game.awayTeam.overtimeLosses
                else if (league == "NFL") awayTeamRecordSuffix = "-" + game.awayTeam.ties
                def homeTeamRecordSuffix = ""
                if (league == "NHL") homeTeamRecordSuffix = "-" + game.homeTeam.overtimeLosses
                else if (league == "NFL") homeTeamRecordSuffix = "-" + game.homeTeam.ties    
                gameTile += "<tr><td width='40%' align=center style='font-size:${fontSize*0.75}%'>${'(' + game.awayTeam.wins + '-' + game.awayTeam.losses + awayTeamRecordSuffix + ')'}</td>"
                gameTile += "<td width='10%' align=center></td>"
                gameTile += "<td width='40%' align=center style='font-size:${fontSize*0.75}%'>${'(' + game.homeTeam.wins + '-' + game.homeTeam.losses + homeTeamRecordSuffix + ')'}</td></tr>"  
            }
            gameTile += "<tr style='padding-bottom: 0em;'><td width='100%' align=center colspan=3>${detailStr}</td></tr>"
            if (parent.showChannel && game.channel != "null" && game.channel != null && !gameFinished) gameTile += "<tr><td width='100%' align=center colspan=3 style='font-size:${fontSize*0.75}%'>${game.channel}</td></tr>"
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
    if (leagueSchedule == "Error: first byte timeout") {
        return leagueSchedule
    }
    else if (leagueSchedule != null) {
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
                key: apiKey,
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
        return "Error: ${e.message}"
    }   
    return result
}

def updateAPICallInfo() {
    parent.updateAPICallInfo(league)
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

