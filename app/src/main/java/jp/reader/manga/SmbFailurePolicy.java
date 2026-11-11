package jp.reader.manga;
/** Errors specific to one folder should not redirect the entire scan to WebDAV. */
final class SmbFailurePolicy {
  static boolean keepSmbForFolder(boolean scanning,boolean isRoot,boolean folderFailure){return scanning&&!isRoot&&folderFailure;}
  static boolean folderStatus(String status){
    if(status==null)return false;
    switch(status){
      case "STATUS_ACCESS_DENIED": case "STATUS_OBJECT_NAME_NOT_FOUND": case "STATUS_OBJECT_PATH_NOT_FOUND":
      case "STATUS_OBJECT_NAME_INVALID": case "STATUS_OBJECT_PATH_INVALID": case "STATUS_NOT_A_DIRECTORY":
      case "STATUS_NO_SUCH_FILE": case "STATUS_DELETE_PENDING": case "STATUS_FILE_IS_A_DIRECTORY":
      case "STATUS_SHARING_VIOLATION": return true;
      default:return false;
    }
  }
}
