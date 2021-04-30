/**
 *  GameTime
 *
 *  Copyright\u00A9 2021 Justin Leonard
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * V1.0 - Initial Release
**/

metadata
{
    definition(name: "GameTime", namespace: "lnjustin", author: "Justin Leonard", importUrl: "")
    {
        capability "Actuator"
        capability "Switch"
        
        attribute "gameTime", "string"
        attribute "gameTimeStr", "string"
        attribute "status", "string"        
        attribute "tile", "string"     
        attribute "opponent", "string"  

    }
}

preferences
{
    section
    {
        input name: "parentID", type: "string", title: "Parent App ID"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}


def logDebug(msg) 
{
    if (logEnable)
    {
        log.debug(msg)
    }
}    

def updated()
{
    configure()
}

def uninstalled()
{
    logDebug("GameTime Parent Device: uninstalled()")
    deleteChildren()
}

def parse(String description)
{
    logDebug(description)
}

def configure()
{    
    refresh()
}

def configureNextGame(data) {
    sendEvent(name: "gameTime", value: data.gameTime)
    sendEvent(name: "gameTimeStr", value: data.gameTimeStr)
    sendEvent(name: "tile", value: data.tile)
    sendEvent(name: "status", value: data.status)
    sendEvent(name: "opponent", value: data.opponent)
    sendEvent(name: "switch", value: data.switch)
}

def updateChildDevice(appID, data)
{
    def child = getChildDevice("GameTimeChildDevice${appID}")
    if (child) {
        child.updateDevice(data)
        updateParentDevice()
    }
    else log.error "No Child Device for app ${appID} found"
}

def updateParentDevice() {
    def nextGameChild = null
    def children = getChildDevices()
    for(child in children)
    {
        def childGameTime = child.currentValue("gameTime")
        if (childGameTime && childGameTime != "No Game Scheduled") {
            if (nextGameChild == null) {
                nextGameChild = child
            }
            else {
                def gameTimeObj = new Date(childGameTime)
                def childStatus = child.currentValue("status ")
                def nextGameChildStatus = nextGameChild.currentValue("status ")
                if (childStatus == "InProgress") {
                    if (nextGameChildStatus != "InProgress") {
                        nextGameChild = child
                    }
                     else if (nextGameChildStatus == "InProgress") {
                          def nextGameTimeObj = new Date(nextGameChild.currentValue("gameTime"))
                         if (nextGameTimeObj.after(gameTimeObj)) {
                             nextGameChild = child    // display whichever game started earlier
                         }
                     }
                 }
                 else {
                     if (nextGameChildStatus != "InProgress") {
                           def nextGameTimeObj = new Date(nextGameChild.currentValue("gameTime"))
                           def now = new Date()
                           if (getSecondsBetweenDates(now, gameTimeObj) < getSecondsBetweenDates(now, nextGameTimeObj)) {
                                nextGameChild = child
                           }
                     }    
                 }
            }
        }
    }
    if (nextGameChild) copyChild(nextGameChild)
    else clearParent()
}


def clearParent() {
    sendEvent(name: "gameTime", value: "No Game Scheduled")
    sendEvent(name: "gameTimeStr", value: "No Game Scheduled")
    sendEvent(name: "tile", value: "<div style='overflow:auto;height:90%'></div>")
    sendEvent(name: "status", value: "No Game Scheduled")
    sendEvent(name: "opponent", value: "No Game Scheduled")
    sendEvent(name: "switch", value: "off")      
}

def copyChild(child) {
    sendEvent(name: "gameTime", value: child.currentValue("gameTime"))
    sendEvent(name: "gameTimeStr", value: child.currentValue("gameTimeStr"))
    sendEvent(name: "tile", value: child.currentValue("tile"))
    sendEvent(name: "status", value: child.currentValue("status"))
    sendEvent(name: "opponent", value: child.currentValue("opponent"))
    sendEvent(name: "switch", value: child.currentValue("switch"))    
}

def createChild(appID, name)
{
    def child = getChildDevice("GameTimeChildDevice${appID}")
    if (!child) {
        String childNetworkID = "GameTimeChildDevice${appID}"
        addChildDevice("lnjustin", "GameTime Child", childNetworkID, [label:name, isComponent:true, name:name])
    }
}

def deleteChild(appID)
{
    deleteChildDevice("GameTimeChildDevice${appID}")
}

def deleteChildren()
{
    for(child in getChildDevices())
    {
        deleteChildDevice(child.deviceNetworkId)
    }
}


def refresh()
{

}
