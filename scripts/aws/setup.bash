#!/bin/bash

sudo apt update && sudo apt upgrade -y
sudo apt install -y gnupg
sudo apt install software-properties-common
sudo add-apt-repository 'deb https://apt.corretto.aws stable main'
sudo apt-get install -y java-11-amazon-corretto-jdk
sudo apt install -y tmux
sudo apt install zip unzip
