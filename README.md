
![GameTimeHeader](https://github.com/user-attachments/assets/9119b9bb-6a22-4f9f-ac63-a00cc65bba35)

# GameTime

- Follow and automate based on the schedules of your favorite professional and college sports teams.
- Display customizable dashboard tile(s) showing the next gametime for your favorite team(s). Display one dashboard tile per team, or display a single tile that displays whatever game is next from among your favorite teams.

![image](https://user-images.githubusercontent.com/12822477/116754970-a79bcb00-a9d7-11eb-8e3e-a2ad18d155a9.png)
- Automate based on whether it's a game day. Switch turns "on" on game day. Off otherwise.
- Automate based on whether it's a scheduled game time.

![image](https://user-images.githubusercontent.com/12822477/116754944-9b177280-a9d7-11eb-8e1a-ffe7eb49242c.png)

- Display tile with your team's upcoming schedule (# games customizable)

**Supported Leagues**<br>
NFL<br>
NBA<br>
MLB<br>
NHL<br>
NCAA Men's Basketball<br>
NCAA Men's Football<br>

**Requirements**
Requires FREE API key per league obtainable from www.sportsdata.io. FREE API key allows 1000 API calls per month.

**Instructions**

1. Install parent and child drivers
2. Install Gametime App as well as the Gametime Pro Instance child app and the Gametime College Instance child app
3. Enable OAuth for the Gametime Pro Instance Child App and the Gametime College Instance Child app
4. Add Gametime as an app
5. Within the Gametime app, add one child app instance per team. When you select your team and click “Done” in the child app, a child device is created for that team.
6. On the Devices page of Hubitat, devices for your teams are listed under the Gametime parent device.

**Non-Live**: This integration is intended to operate based on team schedules, so that you don't miss an opportunity to watch your favorite team and/or so that you can automatically set lighting scenes based on scheduled gametimes. The integration does not pull in live, up-to-date game stats (go watch the game on the TV, not your dashboard!). Although there is a paid API from sportsdata.io for live stats, it's not cost effective for individual use.

**Potential Future Integration**
There's potential for future expansion to Soccer, Golf, Nascar, and more, if there's enough interest in the community.
