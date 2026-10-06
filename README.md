# DiscoveryFTP CloudStream extension

Build:  ./gradlew DiscoveryFtpProvider:make     (needs JDK 17+ and internet for Gradle deps)
Output: DiscoveryFtpProvider/build/DiscoveryFtpProvider.cs3

Install: copy the .cs3 to your phone, then CloudStream > Settings > Extensions > add local plugin,
or push this repo to GitHub (Actions builds plugins.json) and add the repo URL in CloudStream.

Before first build edit repo.json / build.gradle.kts with your GitHub user/repo.
