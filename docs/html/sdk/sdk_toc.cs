
<ul>
  <li><?cs 
   if:android.whichdoc != "online" ?>
    <h2>Android 1.5 SDK, r1</h2><?cs 
   else ?>
    <h2>Current SDK Release</h2><?cs 
   /if ?>
    <ul><?cs 
     if:android.whichdoc == "online" ?>
      <li><a href="<?cs var:toroot ?>sdk/1.1_r1/index.html">Download</a></li><?cs 
     /if ?>
      <li><a href="<?cs var:toroot ?>sdk/1.1_r1/installing.html">Installing</a></li>
      <li><a href="<?cs var:toroot ?>sdk/1.1_r1/upgrading.html">Upgrading</a></li>
      <li><a href="<?cs var:toroot ?>sdk/1.1_r1/requirements.html">System Requirements</a></li>
    </ul>
    <ul>
      <li><a href="<?cs var:toroot ?>sdk/terms.html">SDK Terms and Conditions</a></li>
      <li><a href="<?cs var:toroot ?>sdk/RELEASENOTES.html">SDK Release Notes</a></li>
    </ul><?cs 
 if:android.whichdoc == "online" ?>
  <li>
    <h2>Android System Images</h2>
    <ul>
      <li><a href="<?cs var:toroot ?>sdk/android-1.5.html">Android 1.5 Version Notes</a></li>
      <li><a href="<?cs var:toroot ?>sdk/android-1.1.html">Android 1.1 Version Notes</a></li>
    </ul>
  </li>
  <li>
    <h2>Previous SDK Releases</h2>
    <ul>
      <li><a href="<?cs var:toroot ?>sdk/1.0_r2/index.html">Android 1.0 SDK, release 2</a></li>
      <li><a href="<?cs var:toroot ?>sdk/1.0_r1/index.html">Android 1.0 SDK, release 1</a></li>
      <li><a href="<?cs var:toroot ?>sdk/older_releases.html">Older Releases</a></li>
    </ul>
  </li><?cs 
 /if ?>
</ul>

<script type="text/javascript">
<!--
    buildToggleLists();
//-->
</script>