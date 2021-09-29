# Script to create the update site for official releases

mkdir bjls-update-site
cp releng/p2repository/target/*.zip bjls-update-site
cp -R releng/p2repository/target/repository bjls-update-site/update-site
cp .github/bjls-update-site-index.html bjls-update-site/index.html
