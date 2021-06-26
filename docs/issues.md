## Known Issues with Bazel Eclipse

### Eclipse Titlebar Shows the IntelliJ Icon (Mac)

On Mac, when a file is open in an editor, Eclipse displays the icon for that
  file type based on the file association in the Finder.
This is incorrect behavior, but it is the way it works.
If you have .java files (or other code file types) associated with IntelliJ
  in the Finder, the IntelliJ icon will appear in the Eclipse titlebar.

To fix:
- Find a .java file (or other code file) on the file system with the Finder
- Right click on it and select *Get Info*
- In the Open With combo box, select the application that you would like to associate
  with that file type
- Click the *Change All...* button
- Reboot your computer

You may elect to use Eclipse as the default file association, but it is recommended
  to choose a lightweight editor instead.
When you click on a file in the Finder you generally want to take a look at it, not
  spawn an entire IDE environment.
This will cause the icon in the Eclipse IDE to still be mismatched, but it is less
  confusing than having the IntelliJ icon in the titlebar.
