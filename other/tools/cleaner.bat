@echo off
cd /d %~dp0
java -jar bfg.jar --delete-files "{*.apk,*.json}" ..\..\Release\.git
cd ..\..\Release
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push origin --force --all