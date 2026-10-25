#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
wgram_test_output=$(mktemp -d)
trap 'rm -rf "$wgram_test_output"' EXIT HUP INT TERM
find wgram/tests/stubs -name '*.java' > "$wgram_test_output/sources"
printf '%s\n' TMessagesProj/src/main/java/org/telegram/messenger/wgram/WGramConfig.java TMessagesProj/src/main/java/org/telegram/messenger/wgram/WGramEmojiWire.java TMessagesProj/src/main/java/org/telegram/messenger/wgram/WGramEmojis.java TMessagesProj/src/main/java/org/telegram/messenger/wgram/WGramHistoryPolicy.java TMessagesProj/src/main/java/org/telegram/messenger/wgram/WGramNoticeWire.java wgram/tests/WGramPrivacyTest.java wgram/tests/WGramCoreTest.java wgram/tests/JavaSyntaxCheck.java >> "$wgram_test_output/sources"
javac -encoding UTF-8 -d "$wgram_test_output" @"$wgram_test_output/sources"
java -cp "$wgram_test_output" WGramCoreTest
java -cp "$wgram_test_output" WGramPrivacyTest

java -cp "$wgram_test_output" JavaSyntaxCheck \
    TMessagesProj/src/main/java/org/telegram/messenger/wgram/*.java \
    TMessagesProj/src/main/java/org/telegram/ui/wgram/*.java \
    TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java \
    TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java \
    TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java \
    TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java \
    TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java \
    TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java \
    TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java \
    TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java \
    TMessagesProj/src/main/java/org/telegram/ui/Components/voip/VoIPHelper.java \
    TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java \
    TMessagesProj/src/main/java/org/telegram/messenger/PushListenerController.java \
    TMessagesProj/src/main/java/org/telegram/messenger/GcmPushListenerService.java \
    TMessagesProj/src/main/java/org/telegram/messenger/LiteMode.java \
    TMessagesProj/src/main/java/org/telegram/messenger/UserObject.java \
    TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java \
    TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java \
    TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java \
    TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java \
    TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java \
    TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java \
    TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java \
    TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawableRenderNode.java

