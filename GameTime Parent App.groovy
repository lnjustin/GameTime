/**
 *  GameTime
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
 */
import java.text.SimpleDateFormat
import groovy.transform.Field

definition(
    name: "GameTime",
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

def mainPage() {
    dynamicPage(name: "mainPage") {
    	    installCheck()
		    if(state.appInstalled == 'COMPLETE'){       
                section (getInterface("header", " GameTime")) {
			        section(getInterface("header", " College Sports")) {
				        app(name: "anyOpenApp", appName: "GameTime College Instance", namespace: "lnjustin", title: "<b>Add a new GameTime instance for college sports</b>", multiple: true)
			        }
                    section(getInterface("header", " Professional Sports")) {
				        app(name: "anyOpenApp", appName: "GameTime Professional Instance", namespace: "lnjustin", title: "<b>Add a new GameTime instance for professional sports</b>", multiple: true)
			        } 
                    section("") { 
                        paragraph getInterface("note", txt="After installing or updating your team(s) above, be sure to click the DONE button below.")
                    }
                }
                section (getInterface("header", " Game Tile Settings")) {
                    input("showTeamName", "bool", title: "Show Team Name on Tile?", defaultValue: false, displayDuringSetup: false, required: false)
                    input("showTeamRecord", "bool", title: "Show Team Record on Tile?", defaultValue: false, displayDuringSetup: false, required: false)
                    input("showChannel", "bool", title: "Show TV Channel on Tile?", defaultValue: false, displayDuringSetup: false, required: false)
                    input(name:"fontSize", type: "number", title: "Game Tile Font Size (%)", required:true, submitOnChange:true, defaultValue:100)
                    input("textColor", "text", title: "Game Tile Text Color (Hex)", defaultValue: '#000000', displayDuringSetup: false, required: false)
                    input name: "clearWhenInactive", type: "bool", title: "Clear Tile When Inactive?", defaultValue: false
                    input name: "hoursInactive", type: "number", title: "Inactivity Threshold (In Hours)", defaultValue: 24
                }
                section (getInterface("header", " Schedule Tile Settings")) {
                    input(name:"scheduleFontSize", type: "number", title: "Schedule Tile Font Size (%)", required:true, submitOnChange:true, defaultValue:100)
                    input(name:"oddRowBackgroundColor", type: "text", title: "Background Color (Hex) for Odd Rows of Schedule Tile", required:true, submitOnChange:true, defaultValue: '#FFFFFF')
                    input(name:"oddRowOpacity", type: "number", title: "Opacity (0.0 to 1.0) for Odd Rows of Schedule Tile", required:true, submitOnChange:true, defaultValue: 0)
                    input("oddRowTextColor", "text", title: "Text Color (Hex) for Odd Rows of Schedule Tile", defaultValue: '#000000', displayDuringSetup: false, required: false)   
                    input(name:"evenRowBackgroundColor", type: "text", title: "Background Color (Hex) for Even Rows of Schedule Tile", required:true, submitOnChange:true, defaultValue: '#9E9E9E')
                    input(name:"evenRowOpacity", type: "number", title: "Opacity (0.0 to 1.0) for Even Rows of Schedule Tile", required:true, submitOnChange:true, defaultValue: 1)
                    input("evenRowTextColor", "text", title: "Text Color (Hex) for Even Rows of Schedule Tile", defaultValue: '#FFFFFF', displayDuringSetup: false, required: false)                      
                }
			    section (getInterface("header", " General Settings")) {
                    input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false)
		        }
            }
            section("") {
                
                footer()
            }
    }
}

 public static getRGB(String hex)
{
    def rgb = []
    for (int i = 0; i < 3; i++)
    {
        rgb[i] = (Integer.parseInt(hex.substring(i * 2 + 1, i * 2 + 3), 16)).toString()
    }
    return rgb
}

def getScheduleTileTextColor(oddOrEven) {
    def color = null
    if (oddOrEven == "odd") {
         color = (oddRowTextColor) ? oddRowTextColor : '#000000'
    }
    else if (oddOrEven == "even") {
        color = (evenRowTextColor) ? evenRowTextColor : '#FFFFFF'
    }
    return color
}

def getScheduleTileFontSize() {
    return scheduleFontSize != null ? scheduleFontSize : 100
}

def getScheduleTileBackgroundColor(oddOrEven) {
    def color = null
    def hex = null
    def op = null
    if (oddOrEven == "odd") {
         hex = (oddRowBackgroundColor) ? oddRowBackgroundColor : '#FFFFFF'
         op = oddRowOpacity ? oddRowOpacity : 0
    }
    else if (oddOrEven == "even") {
        hex = (evenRowBackgroundColor) ? evenRowBackgroundColor : '#9E9E9E'
         op = evenRowOpacity ? evenRowOpacity : 1 
    }
    def rgb = getRGB(hex)
    def opacity = op.toString()
    return "rgba(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + "," + opacity + ")"    
}

def getTextColorSetting() {
    return (textColor) ? textColor : "#000000"
}

def getFontSizeSetting() {
    return fontSize != null ? fontSize : 100
}

def getInactivityThresholdSetting() {
    return hoursInactive != null ? hoursInactive : 24
}

def getClearWhenInactiveSetting() {    
   // logDebug("In getClearWhenInactive() in parent")
    return clearWhenInactive != null ? clearWhenInactive : false
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
    def storedAPICalls = state.apiCallsThisMonth
    def numMonths = state.numMonthsInstalled
    state.clear()
    if (storedAPICalls != null) state.apiCallsThisMonth = storedAPICalls
    if (numMonths != null) state.numMonthsInstalled = numMonths
	initialize()
}

def uninstalled() {
    deleteDevices()
	logDebug "Uninstalled app"
}

def initialize() {
    createParentDevice()
    childApps.each { child ->
        child.updated()                
    }
}

def getLeagueAPIKey(forAppID, forLeague) {
    def leagueKey = null
    childApps.each { child ->
        if (child.id != forAppID) {
            def childKey = child.getLeagueAPIKey(forLeague)               
            if (childKey != null) leagueKey = childKey
        }
    }    
    return leagueKey
}

def updateLastGameResult(appID) {
    childApps.each { child ->
        if (child.id == appID) {
            child.updateRecord(true)                
        }
    }
}

def fullUpdate(appID) {
    childApps.each { child ->
        if (child.id == appID) {
            child.update()                
        }
    }
}

def installCheck(){
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	}
  	else{
    	log.info "Parent Installed"
  	}
}

def createParentDevice()
{
    def parent = getChildDevice("GameTimeParentDevice${app.id}")
    if (!parent) {
        String parentNetworkID = "GameTimeParentDevice${app.id}"
        parent = addChildDevice("lnjustin", "GameTime", parentNetworkID, [label:"GameTime", isComponent:true, name:"GameTime"])
        if (parent) {
            parent.updateSetting("parentID", app.id)
            logDebug("Created GameTime Parent Device")
        }
        else log.error "Error Creating GameTime Parent Device"
    }
}

def deleteDevices() 
{
    deleteChildrenDevices()
    deleteChildDevice("GameTimeParentDevice${app.id}")
}

def deleteChildrenDevices() 
{
    def parent = getChildDevice("GameTimeParentDevice${app.id}")
    if (parent) {
        parent.deleteChildren()
    }
    else log.error "No Parent Device Found. No child devices deleted."    
}

def deleteChildDevice(appID) {
    def parent = getChildDevice("GameTimeParentDevice${app.id}")
    if (parent) {
        parent.deleteChild(appID)
    }
    else log.error "No Parent Device Found."
}

def createChildDevice(appID, name, isLowPriority, lowPriorityThreshold) {
    def parent = getChildDevice("GameTimeParentDevice${app.id}")
    if (parent) {
        parent.createChild(appID, name, isLowPriority, lowPriorityThreshold)
    }
    else log.error "No Parent Device Found."
}

def updateChildDevice(appID, data) {
    def parent = getChildDevice("GameTimeParentDevice${app.id}")
    if (parent) {
        parent.updateChildDevice(appID, data)
    }
    else log.error "No Parent Device Found."
}

def pushDeviceButton(appID, buttonNum) {
    def parent = getChildDevice("GameTimeParentDevice${app.id}")
    if (parent) {
        parent.pushChildDeviceButton(appID, buttonNum)
    }
    else log.error "No Parent Device Found."    
}

def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
	}
}

def countAPICall(league) {
    if (state.apiCallsThisMonth == null) state.apiCallsThisMonth = [:]
    if (state.apiCallsThisMonth[league] != null)  state.apiCallsThisMonth[league]++
    else if (state.apiCallsThisMonth[league] == null) state.apiCallsThisMonth[league] = 1
    if (state.apiCallsThisMonth[league] > 1000) log.warn "API Call Limit of 1000 per month exceeded for ${league}. Uncheck 'Clear Teams Data Between Updates' in the app to reduce the number of API calls."
}
    
def updateAPICallInfo(league) {
    Calendar cal = Calendar.getInstance()
    cal.setTimeZone(location.timeZone)
    cal.setTime(new Date())
    def dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    def isMonthStart = dayOfMonth == 1 ? true : false    
    if (isMonthStart) {
        if (state.numMonthsInstalled == null) state.numMonthsInstalled = [:]
        if (state.numMonthsInstalled[league] == null) {
            state.numMonthsInstalled[league] = 0 // don't start average yet since only installed part of the month
            if (state.apiCallsThisMonth == null) state.apiCallsThisMonth = [:]
            state.apiCallsThisMonth[league] = 0
        }
        else {
            state.numMonthsInstalled[league]++
            if (state.avgAPICallsPerMonth == null) state.avgAPICallsPerMonth = [:]
            if (state.avgAPICallsPerMonth[league] != null) {
                state.avgAPICallsPerMonth[league] = state.avgAPICallsPerMonth[league] + ((state.apiCallsThisMonth[league] - state.avgAPICallsPerMonth[league]) / state.numMonthsInstalled[league])
            }
            else {
                state.avgAPICallsPerMonth[league] = state.apiCallsThisMonth[league]
            }           
            state.apiCallsThisMonth[league] = 0
        }
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

