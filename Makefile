.PHONY: clean compile install debug release

all: clean compile install debug release

clean:
	mvn clean

compile:
	mvn compile hpi:hpi

install: clean
	mvn install

debug: install
	mvn hpi:run

release:
	mvn release:clean release:prepare release:perform
