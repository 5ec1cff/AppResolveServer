# AppResolveServer

## Usage

1. Build

```
gradlew pushByADBDebug|Release
```

2. Execute `/data/local/tmp/ars`

It's also the script at app/scripts/ars.sh

```
/data/local/tmp/ars -n <socketname> [-s true|false] [-r true|false] 
```

3. Use

```
echo '{"requireIcons":true}' | socat -t100 - abstract:pkg
```

Request Format (JSON):

```json lines
{
  // Does the result contain packages? default: true
  "requirePackages": true,
  // Does the result contain users? default: true
  "requireUsers": true,
  // Does the result packages contain labels? default: true
  "requireLabels": true,
  // Does the result packages contain icons? default: false
  "requireIcons": false,
  // Filter out system apps? default: false
  "filterSystem": false,
  // Filter out apps without components? default: true
  "filterNoComponents": true,
  // Only request these packages (string array, empty or null meaning request all) default: []
  "packageNames": [],
  // Only request these user ids (int array, empty or null meaning request all) default: []
  "users": [],
  // Icon width in pixels, < 0 meaning original size, default: -1
  "iconW": -1,
  // Icon height in pixels, < 0 meaning original size, default: -1
  "iconH": -1
}
```

Response Format (JSON)

```json lines
{
  "packages": [
    {
      // packageName
      "packageName": "",
      // installed users of packages, subset of `users` of request if not empty
      "users": [],
      // package label, only valid if requireLabels = true
      "label": "",
      // icon data url, only valid if requireIcons = true
      "icon": "",
      // App id (uid) of package
      "appId": 0,
      // Last update time stamp (in string), can be used for sorting
      "lastUpdate": ""
    }
  ],
  "users": [
    {
      // user id (integer)
      "id": 0,
      // user name, can be empty
      "name": ""
    }
  ]
}
```

