# The Google Mobile Ads SDK pulls in old WorkManager (2.7) and Room (2.2) versions. Their keep rules
# keep these classes but not their no-argument constructors, which R8 then removes. Both libraries
# create these classes by reflection, so without these rules the release build crashes on launch
# ("Failed to create an instance of androidx.work.impl.WorkDatabase").
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.InputMerger { <init>(); }
