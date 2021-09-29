# Script to create the update site for BEF test release candidates

mkdir bef-rc-staging
cp releng/p2repository/target/*.zip bef-rc-staging
cp -R releng/p2repository/target/repository bef-rc-staging/update-site-release-candidate
cp .github/bef-update-site-index.html bef-rc-staging/index.html
