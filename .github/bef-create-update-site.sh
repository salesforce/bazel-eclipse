# Script to create the update site for official releases

mkdir update-site
cp releng/p2repository/target/*.zip update-site
cp -R releng/p2repository/target/repository update-site/update-site
cp .github/bef-update-site-index.html update-site/index.html
