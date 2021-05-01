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
 *
 *    Date        Who            What
 *    ----        ---            ----
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
	page name: "apiAccessPage", title: "Calendarific API Access", install: false, nextPage: "mainPage"

}

@Field leagues = ["MLB", "NBA", "NFL", "NHL", "WNBA"]
@Field api = ["NBA":"nba", "MLB":"mlb", "NFL":"nfl", "NHL":"nhl", "WNBA":"wnba"]

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
                    input(name:"team", type: "enum", title: "Team", options: getTeamOptions(), required:true)
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
        setTeam()
        createChild()        
        setNextGame()
        schedule("01 00 00 ? * *", setSeason)
        schedule("15 00 00 ? * *", setNextGame)
    }
    else log.error "Missing input fields."
}

def getDateObj(dateStr) {
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

def setNextGame() {
    def schedule = fetchTeamSchedule()
    def now = new Date()
    def nextGame = null
    for (game in schedule) {
        def dateTime = null
        if ((game.DateTime == null || game.DateTime == "null") && (game.Day != null && game.Day != "null")) dateTime = game.Day  // game on Day but time not yet set
        else if (game.DateTime != null && game.DateTime != "null") dateTime = game.DateTime
        def gameTime = getDateObj(dateTime)
        def status = game.Status 
        if (gameTime.after(now) || gameTime.equals(now) || status == "InProgress") {
            if (nextGame == null) nextGame = game
            else {
                if (status == "InProgress") {
                    if (nextGame.Status != "InProgress") nextGame = game
                    else if (nextGame.Status == "InProgress") {
                        def nextDateTime = null
                        if ((nextGame.DateTime == null || nextGame.DateTime == "null") && (nextGame.Day != null && nextGame.Day != "null")) nextDateTime = nextGame.Day  // game on Day but time not yet set
                        else if (nextGame.DateTime != null && nextGame.DateTime != "null") nextDateTime = nextGame.DateTime
                        def nextGameTime = getDateObj(nextDateTime)
                        if (nextGameTime.after(gameTime)) nextGame = game    // display whichever game started earlier
                    }
                }
                else {
                    if (nextGame.Status != "InProgress") {
                        def nextDateTime = null
                        if ((nextGame.DateTime == null || nextGame.DateTime == "null") && (nextGame.Day != null && nextGame.Day != "null")) nextDateTime = nextGame.Day  // game on Day but time not yet set
                        else if (nextGame.DateTime != null && nextGame.DateTime != "null") nextDateTime = nextGame.DateTime
                        def nextGameTime = getDateObj(nextDateTime)
                        if (getSecondsBetweenDates(now, gameTime) < getSecondsBetweenDates(now, nextGameTime)) {
                            nextGame = game
                        }
                    }
                }
            }
        }
    }
    if (nextGame == null) {
        def tile = getGameTile(null, null, null, null)
        def gameMap = [gameTime: "No Game Scheduled", gameTimeStr: "No Game Scheduled", status: "No Game Scheduled", opponent: "No Game Scheduled", channel: "No Game Scheduled", timeRemaining: "No Game Scheduled", tile: tile, switch: "off"]
        updateDevice(gameMap)
    }
    else {
        def switchStatus = "off"
        def nextDateTime = null
        if ((nextGame.DateTime == null || nextGame.DateTime == "null") && (nextGame.Day != null && nextGame.Day != "null")) nextDateTime = nextGame.Day  // game on Day but time not yet set
        else if (nextGame.DateTime != null && nextGame.DateTime != "null") nextDateTime = nextGame.DateTime
        def gameTimeObj = getDateObj(nextDateTime)
        def gameTime = gameTimeObj.getTime()
        def nextWeek = new Date() + 7
        def dateFormat = null
        def gameTimeStrPrefix = ""
        if (gameTimeObj.after(nextWeek)) dateFormat = new SimpleDateFormat("EEE, MMM d h:mm a")
        else if (isToday(gameTimeObj)) {
            gameTimeStrPrefix = "Today "
            dateFormat = new SimpleDateFormat("h:mm a")
            switchStatus = "on"
        }
        else dateFormat = new SimpleDateFormat("EEE h:mm a")
        dateFormat.setTimeZone(location.timeZone)        
        def gameTimeStr = gameTimeStrPrefix + dateFormat.format(gameTimeObj)
        def status = nextGame.Status        
        def channel = nextGame.Channel
        def homeTeam = null 
        def awayTeam = null
        def opponent = null
        if (nextGame.HomeTeam == state.team.key) {
            homeTeam = state.team
            awayTeam = getTeam(nextGame.AwayTeam)
            opponent = awayTeam
        }
        else if (nextGame.AwayTeam == state.team.key) {
            awayTeam = state.team
            homeTeam = getTeam(nextGame.HomeTeam)
            opponent = homeTeam
        }
        else log.error "Team Not Playing in Next Game"
        def opponentName = opponent.displayName
            
        def tile = null
        
        if (status == "InPrgoress") {
            def progressStr = ""
            if (league == "NFL") {
                def gameData = fetchGameData(nextGame.ScoreID)
                if (gameData.quarter == "1" || gameData.quarter == "2" || gameData.quarter == "3" || gameData.quarter == "4") {
                    progressStr = gameData.quarter + "Q " + gameData.timeRemaining
                }
                else if (gameData.quarter == "Half") progressStr = gameData.quarter
                else if (gameData.quarter == "OT") progressStr = gameData.quarter + " " + gameData.timeRemaining
            }
            else if (league == "MLB") {
                if (nextGame.InningHalf == "T") progressStr = "Top "
                else if (nextGame.InningHalf == "B") progressStr = "Bot "
                progressStr += nextGame.Inning
            }
            else if (league == "NBA") {
                def quarter = nextGame.Quarter
                if (quarter == "1" || quarter == "2" || quarter == "3" || quarter == "4") {
                    progressStr = quarter + "Q " + nextGame.TimeRemainingMinutes + ":" + nextGame.TimeRemainingSeconds
                }
                else if (quarter == "Half") progressStr = quarter
                else if (quarter == "OT") progressStr = quarter + " " + nextGame.TimeRemainingMinutes + ":" + nextGame.TimeRemainingSeconds
            }
            else if (league == "NHL") {
                def timeRemaining = nextGame.TimeRemainingMinutes + ":" + nextGame.TimeRemainingSeconds
                def period = nextGame.Period
                if (period == "1") progressStr = "1st " + timeRemaining
                else if (period == "2") progressStr = "2nd " + timeRemaining
                else if (period == "3") progressStr = "3rd " + timeRemaining
                else if (period == "SO") progressStr = period
                else if (quarter == "OT") progressStr = period + " " + timeRemaining
            }
            tile = getGameTile(homeTeam, awayTeam, progressStr, channel)
        }
        else {
            tile = getGameTile(homeTeam, awayTeam, gameTimeStr, channel)
        }

        def gameMap = [appID: app.id, gameTime: gameTime, gameTimeStr: gameTimeStr, status: status, opponent: opponentName, tile: tile, switch: switchStatus]
        updateDevice(gameMap)
        
        def delayedGameTimeObj = null
        // update game after the 10 minute delay from SportsData.IO
        use(TimeCategory ) {
            delayedGameTimeObj = gameTimeObj + 11.minutes
        }
        if (status == "InProgress") runIn(300, setNextGame) // while game is in progress, update every 5 minutes
        else if (delayedGameTimeObj.after(new Date())) {
            runOnce(delayedGameTimeObj, setNextGame)
        }
    }
}

def getGameTile(homeTeam, awayTeam, detailStr, channel) {
    def gameTile = null
    def textColor = parent.getTextColor()
    def colorStyle = ""
    if (textColor != "#000000") colorStyle = "color:" + textColor
    if (homeTeam != null && awayTeam != null) {
        gameTile = "<div style='overflow:auto;height:90%;${colorStyle}'><table width='100%'>"
        gameTile += "<tr><td width='40%' align=center><img src='${awayTeam.logo}' width='100%'></td>"
        gameTile += "<td width='10%' align=center>at</td>"
        gameTile += "<td width='40%' align=center><img src='${homeTeam.logo}' width='100%'></td></tr>"
        if (parent.showTeamName) {
            gameTile += "<tr style='padding-bottom: 0em'><td width='40%' align=center>${awayTeam.name}</td>"
            gameTile += "<td width='10%' align=center></td>"
            gameTile += "<td width='40%' align=center>${homeTeam.name}</td></tr>" 
        }
        if (parent.showTeamRecord) {
            gameTile += "<tr><td width='40%' align=center style='font-size:75%'>${'(' + awayTeam.wins + '-' + awayTeam.losses + ')'}</td>"
            gameTile += "<td width='10%' align=center></td>"
            gameTile += "<td width='40%' align=center style='font-size:75%'>${'(' + homeTeam.wins + '-' + homeTeam.losses + ')'}</td></tr>"  
        }
        gameTile += "<tr style='padding-bottom: 0em'><td width='100%' align=center colspan=3>${detailStr}</td></tr>"
        if (parent.showChannel) gameTile += "<tr><td width='100%' align=center colspan=3 style='font-size:75%'>${channel}</td></tr>"
        gameTile += "</table></div>"  
    }
    else gameTile = "<div style='overflow:auto;height:90%'></div>"
    return gameTile
}

def getTeam(key) {
    def teams = getTeams()
    def returnTeam = null
    for (tm in teams) {
        if(key == tm.key) {
            returnTeam = tm
        }
    }
    returnTeam
}

def updateDevice(data) {
    parent.updateChildDevice(app.id, data)
}

def getTeamOptions() {
    setSeason()
    def teams = getTeams()
    def teamOptions = []
    for (tm in teams) {
        teamOptions.add(tm.displayName)
    }
    return teamOptions
}

def setTeam() {
    def teams = getTeams()
    for (tm in teams) {
        if(settings["team"] == tm.displayName) {
            state.team = tm
        }
    }
}

def fetchTeamSchedule() {
    def leagueSchedule = null
    if (state.season) {
        if (league == "NFL") leagueSchedule = sendApiRequest("/scores/json/Schedules/" + state.season)
        else leagueSchedule = sendApiRequest("/scores/json/Games/" + state.season)
    }
    def teamSchedule = []
    if (leagueSchedule) {
        for (game in leagueSchedule) {
            if (game.AwayTeam == state.team.key || game.HomeTeam == state.team.key) {
                logDebug("Adding game to team schedule with gametime of ${game.DateTime}")
                teamSchedule.add(game)
            }
        }
    }
    else log.warn "No upcoming schedule for ${team} in ${league}"
    if (teamSchedule == []) log.warn "No upcoming schedule for ${team} in ${league}"
    return teamSchedule
}

def fetchGameData(id) {
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

def getTeams() {
   def fullTeams = fetchTeams()
   def standings = fetchStandings()
   def partialTeams = []
   for (tm in fullTeams) {
       def wins = null
       def losses = null
       def ties = null
       for (standing in standings) {
           def standingKey = null
           if (league == "NFL") standingKey = standing.Team
           else if (league != "WNBA") standingKey = standing.Key
               // WNBA does not have standing in the API
           if (standingKey == tm.Key) {
              // logDebug("Found ${tm.Key} in standings with W: ${standing.Wins}, L: ${standing.Losses}")
               wins = standing.Wins
               losses = standing.Losses
               ties = standing.Ties
           }
       }
       def displayName = ""
       if (league == "NFL") displayName = tm.FullName
       else displayName = tm.City + " " + tm.Name
       def teamMap = [id: tm.TeamID, key: tm.Key, city: tm.City, name: tm.Name, logo: tm.WikipediaLogoUrl, displayName: displayName, wins: wins, losses: losses, ties:ties]
       partialTeams.add(teamMap)
   }
   return partialTeams
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

