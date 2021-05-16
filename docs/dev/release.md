## Releasing Bazel Eclipse

This document is for maintainers.
This is the set of steps for releasing a new version of BEF.

## Release Process

### Setting the Version

The version is set in a number of files, both for Maven and for OSGi (Eclipse).
For Maven pom.xml files, keep the *SNAPSHOT* suffix (e.g. *1.3.1-SNAPSHOT*).
For OSGi meta files, keep the *qualifier* suffix (e.g. *1.3.1.qualifier*).

Tycho has commands for this, and is a good start, but they don't do the full job:
```
 mvn -X -DnewVersion=1.3.1-SNAPSHOT tycho-versions:set-version
 mvn -X tycho-versions:update-eclipse-metadata
```

In the end, you will need to manually ensure the new version is applied everywhere.
This [PR for version 1.3.1](https://github.com/salesforce/bazel-eclipse/pull/225/files)
  gives you an idea.
Grepping through the code base looking for the old versions is not a bad idea.

Verify all the files are in order before submitting to master with this command:
```
mvn clean verify
```

### Local Build and Testing

Prior to releasing BEF, please test it from an install.
Launching BEF (as inner eclipse) from the Eclipse SDK (outer eclipse) is not a sufficient test.
Certain packaging problems will not appear when launched from Eclipse SDK.
Instead, please download the latest JEE Eclipse to test with.

It will build BEF and write it into the _releng/p2repository/target/_ directory.
You can install the .zip or from the _repository_ directory in that location.
Use the standard Help -> Install New Software... menu.
Our [update site](https://opensource.salesforce.com/bazel-eclipse/) has instructions.

Make sure to run through an import of a decently complex Bazel workspace.
Things to look for:
- The Bazel Classpath is correct for each project
- There are no build errors in the Problems View
- Open a .java file in an editor an make sure there aren't any red squigglies

### Releasing from GitHub

:fire: The update site is our live installation location for our users. If you are doing
experimental work with the release process, please do not release directly to _gh-pages_
branch. Update the _github-pages-deploy-action_ stage in the workflow to a test branch
if you have **any** doubts about what you are releasing. Be prepared to rollback the
[gh-pages branch](https://github.com/salesforce/bazel-eclipse/tree/gh-pages) branch at
the first sign of trouble.

Our GitHub repo has our release process implemented as a GitHub Actions workflow.
To build the release, make sure you have your versions set and committed to master (see above).

Then, run the release workflow:
 - Navigate to the [Bazel Eclipse Actions](https://github.com/salesforce/bazel-eclipse/actions) tab in GitHub
 - Click on the _Bazel Eclipse Feature Release_ button in the left nav
 - Click on the _Run Workflow_ button on the right side, and pick the branch to release from (normally _master_)
 - Monitor the build, it should take about a minute
 - Navigate to the [gh-pages branch](https://github.com/salesforce/bazel-eclipse/tree/gh-pages) of the repo. It contains all the files from the release.
   - Verify the files look to be correct (i.e. check version in the filenames)

### Test the Update Site

First, check that our landing page is available and has correct instructions:

- [BEF Update Site landing page](https://opensource.salesforce.com/bazel-eclipse/)

Next, follow the instructions on the page and install BEF into a fresh copy of Eclipse.
Finally, repeat the install test with the zip file that is in the root directory of the *gh-pages* branch

If anything fails, revert the changes in the _gh-pages_ branch ASAP!
It is the live update site for all of our users.

```
# assume release 1.3.2 has a problem, revert gh-pages back to 1.3.1
git checkout gh-pages
git reset --hard v1.3.1.updatesite
git push --force origin gh-pages
```

### Tag the Release

After verifying the release, please tag both the _master_ and _gh-pages_ branches.

```
git fetch
git checkout master
git tag v1.3.1
git push origin v1.3.1
git checkout gh-pages
git tag v1.3.1.updatesite
git push origin v1.3.1.updatesite
```

### Announcing the Release

After the release has been confirmed, please create a new release in our
  [Releases](https://github.com/salesforce/bazel-eclipse/releases) list.
Please follow the pattern of previous releases.

## Release Implementation

The release workflow is defined using GitHub Actions.
Instead of explaining how it works, here are some pointers to some interesting files:

- [Release workflow definition](../../.github/workflows/build-release-deploy.yml)
- [Update site layout script](../../.github/create-update-site.sh)
