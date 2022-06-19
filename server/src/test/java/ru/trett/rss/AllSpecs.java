package ru.trett.rss;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import ru.trett.rss.core.FeedServiceSpecs;
import ru.trett.rss.core.UserServiceSpecs;

@RunWith(Suite.class)
@SuiteClasses({FeedParserSpecs.class, FeedServiceSpecs.class, UserServiceSpecs.class})
public class AllSpecs {}
