# Script to create the update site for test release candidates

mkdir update-site-release-candidate
cp releng/p2repository/target/*.zip update-site-release-candidate
cp -R releng/p2repository/target/repository update-site-release-candidate/update-site
cp .github/update-site-index.html update-site-release-candidate/index.html
