import "dart:convert";
import "dart:core";
import "dart:io";

import "package:flutter/src/foundation/print.dart" show debugPrint;

void main(List<String> args) async {
  var location = Platform.script.toString();
  final isNewFlutter = location.contains(".snapshot");
  if (isNewFlutter) {
    final sp = Platform.script.toFilePath();
    final sd = sp.split(Platform.pathSeparator);
    sd.removeLast();
    final scriptDir = sd.join(Platform.pathSeparator);
    final packageConfigPath = [
      scriptDir,
      "..",
      "..",
      "..",
      "package_config.json",
    ].join(Platform.pathSeparator);
    final jsonString = File(packageConfigPath).readAsStringSync();
    final packages = jsonDecode(jsonString) as Map<String, dynamic>;
    final packageList = packages["packages"] as List;
    String? zoomFileUri;
    for (final package in packageList) {
      if (package["name"] == "gr_zoom") {
        zoomFileUri = package["rootUri"] as String?;
        break;
      }
    }
    if (zoomFileUri == null) {
      debugPrint("gr_zoom package not found!");
      return;
    }
    location = zoomFileUri;
  }
  if (Platform.isWindows) {
    location = location.replaceFirst("file:///", "");
  } else {
    location = location.replaceFirst("file://", "");
  }
  if (!isNewFlutter) {
    location = location.replaceFirst("/bin/unzip_zoom_sdk.dart", "");
  }
  // var filename =
  //     location + '/ios-sdk/MobileRTC${(args.length == 0) ? "" : "-dev"}.zip';

  await checkAndDownloadSDK(location);
  // print('Decompressing ' + filename);

  // final bytes = File(filename).readAsBytesSync();

  // final archive = ZipDecoder().decodeBytes(bytes);

  // var current = new File(location + '/ios/MobileRTC.framework/MobileRTC');
  // var exist = await current.exists();
  // if (exist) current.deleteSync();

  // for (final file in archive) {
  //   final filename = file.name;
  //   if (file.isFile) {
  //     final data = file.content as List<int>;
  //     File(location + '/ios/MobileRTC.framework/' + filename)
  //       ..createSync(recursive: true)
  //       ..writeAsBytesSync(data);
  //   }
  // }

  debugPrint("Complete");
}

Future<void> checkAndDownloadSDK(String location) async {
  final iosSDKFile = "$location/ios/MobileRTC.xcframework/ios-arm64/MobileRTC.framework/MobileRTC";
  bool exists = await File(iosSDKFile).exists();

  if (!exists) {
    await downloadFile(
      Uri.parse(
        "https://com21-static.s3.sa-east-1.amazonaws.com/zoom/ios/5.14.5/ios-arm64/MobileRTC?dl=1",
      ),
      iosSDKFile,
    );
  }

  final iosSimulateSDKFile =
      "$location/ios/MobileRTC.xcframework/ios-arm64_x86_64-simulator/MobileRTC.framework/MobileRTC";
  exists = await File(iosSimulateSDKFile).exists();

  if (!exists) {
    await downloadFile(
      Uri.parse(
        "https://com21-static.s3.sa-east-1.amazonaws.com/zoom/ios/5.14.5/ios-arm64_x86_64-simulator/MobileRTC",
      ),
      iosSimulateSDKFile,
    );
  }

  final androidCommonLibFile = "$location/android/libs/commonlib.aar";
  exists = await File(androidCommonLibFile).exists();
  if (!exists) {
    await downloadFile(
      Uri.parse(
        "https://com21-static.s3.sa-east-1.amazonaws.com/zoom/android/5.14.5/commonlib.aar?dl=1",
      ),
      androidCommonLibFile,
    );
  }
  final androidRTCLibFile = "$location/android/libs/mobilertc.aar";
  exists = await File(androidRTCLibFile).exists();
  if (!exists) {
    await downloadFile(
      Uri.parse(
        "https://com21-static.s3.sa-east-1.amazonaws.com/zoom/android/5.14.5/mobilertc.aar?dl=1",
      ),
      androidRTCLibFile,
    );
  }
}

Future<void> downloadFile(Uri uri, String savePath) async {
  debugPrint("Download $uri to $savePath");
  final File destinationFile = await File(savePath).create(recursive: true);
  // var dio = Dio();
  // dio.options.connectTimeout = 1000000;
  // dio.options.receiveTimeout = 1000000;
  // dio.options.sendTimeout = 1000000;
  // await dio.downloadUri(uri, savePath);
  final request = await HttpClient().getUrl(uri);
  final response = await request.close();
  await response.pipe(destinationFile.openWrite());
}
