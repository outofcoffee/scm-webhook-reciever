# Change Log

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added
- Changelog (this file)

## [0.1.2] - 2018-08-25
### Added
- Adds MongoDB implementation of pull request event DAO.
- Adds analysis entry and DSL property exposing number of passed builds for a commit (fixes #14).

## [0.1.1] - 2018-08-23
### Added
- Adds persistence layer for pending actions. Using in-memory implementation for now.
- Adds persistence layer for pull requests. Using in-memory implementation for now.
- Looks up pull request for a commit (fixes #13).
- Improves release scripts.

## [0.1.0] - 2018-18-23
### Added
- Adds support for exclusive actions.
- Allows branch and repo names to be regular expressions.
- Improves test coverage.

### Fixed
- Ensures analysis is posted either exactly once (or never).